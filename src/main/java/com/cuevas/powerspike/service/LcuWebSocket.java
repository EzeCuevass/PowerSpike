package com.cuevas.powerspike.service;

import com.cuevas.powerspike.dto.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import javax.net.ssl.*;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

@Service
public class LcuWebSocket {

    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int RECONNECT_DELAY_MS = 2000;

    private final LcuApi lcuApi;
    private final LcuLockfile lcuLockfile;
    private final ObjectMapper mapper = new ObjectMapper();
    private WebSocketClient client;
    private int reconnectAttempts;
    private boolean shouldStop;

    public LcuWebSocket(@Lazy LcuApi lcuApi, LcuLockfile lcuLockfile) {
        this.lcuApi = lcuApi;
        this.lcuLockfile = lcuLockfile;
    }

    public synchronized void connectToLCU() {
        if (!lcuLockfile.isConnected()) return;
        shouldStop = false;
        reconnectAttempts = 0;
        disconnect();

        int port = lcuLockfile.getPort();
        String auth = "Basic " + Base64.getEncoder().encodeToString(
            ("riot:" + lcuLockfile.getToken()).getBytes()
        );
        String finalAuth = auth;

        try {
            SSLContext sslContext = createSslContext();
            URI uri = new URI("wss://127.0.0.1:" + port);

            client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println(">>> [WS] Conectado a wss://127.0.0.1:" + port + "/");
                    send("[5, \"OnJsonApiEvent\"]");
                    System.out.println(">>> [WS] Suscrito a TODOS los eventos del cliente");
                    reconnectAttempts = 0;
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println(">>> [WS] Desconectado. Código: " + code + " Razón: " + reason);
                    if (!shouldStop && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                        reconnectAttempts++;
                        System.out.println(">>> [WS] Reintentando (" + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")...");
                        try {
                            Thread.sleep(RECONNECT_DELAY_MS);
                            connectToLCU();
                        } catch (InterruptedException ignored) {}
                    }
                }

                @Override
                public void onError(Exception ex) {
                    System.out.println(">>> [WS] Error: " + ex.getMessage());
                }
            };

            client.addHeader("Authorization", finalAuth);
            client.setSocketFactory(sslContext.getSocketFactory());
            client.connectBlocking();

        } catch (Exception e) {
            System.out.println(">>> [WS] Error conectando: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    private void handleMessage(String raw) {
        if (!raw.contains("OnJsonApiEvent")) return;

        try {
            JsonNode root = mapper.readTree(raw);
            if (!root.isArray() || root.size() < 3) return;

            String uri = root.get(2).has("uri") ? root.get(2).get("uri").asText() : "";
            if (!uri.contains("/lol-champ-select/v1/session")) return;

            String jsonPart = root.get(2).toString();
            LcuWsEventDataDTO eventData = mapper.readValue(jsonPart, LcuWsEventDataDTO.class);
            if (eventData == null || eventData.data() == null) return;

            LcuChampSelectDTO session = eventData.data();
            LcuChampSelectDTO previous = lcuApi.getCurrentChampSelect();

            System.out.println(">>> [WS] Evento: " + eventData.eventType());

            // No actualizar si el evento es Delete (champ select terminó) o si los equipos están vacíos
            // Esto preserva el último champ select válido para el análisis post-game
            if ("Delete".equals(eventData.eventType())) {
                System.out.println(">>> [WS] Evento Delete ignorado, manteniendo último champ select válido");
                return;
            }

            if (session.myTeam() == null || session.myTeam().isEmpty()) {
                System.out.println(">>> [WS] Equipos vacíos, ignorando evento");
                return;
            }

            if (session.timer() != null) {
                long secs = session.timer().adjustedTimeLeftInPhase() / 1000;
                System.out.println(">>> [WS] Timer: " + session.timer().phase() + " - " + secs + "s restantes");
            }

            for (LcuTeamMemberDTO m : session.myTeam()) {
                String name = m.gameName() + "#" + m.tagLine();
                if (m.championId() > 0) {
                    System.out.println(">>> [WS] " + name + " pickeó " + lcuApi.getChampionName(m.championId()));
                }
                if (m.championPickIntent() > 0 && (previous == null || !hasPickIntent(previous.myTeam(), m.cellId(), m.championPickIntent()))) {
                    System.out.println(">>> [WS] " + name + " está hovereando " + lcuApi.getChampionName(m.championPickIntent()));
                }
            }

            for (LcuTeamMemberDTO m : session.theirTeam()) {
                String name = m.gameName() + "#" + m.tagLine();
                if (m.championId() > 0) {
                    System.out.println(">>> [WS] " + name + " pickeó " + lcuApi.getChampionName(m.championId()));
                }
                if (m.championPickIntent() > 0 && (previous == null || !hasPickIntent(previous.theirTeam(), m.cellId(), m.championPickIntent()))) {
                    System.out.println(">>> [WS] " + name + " está hovereando " + lcuApi.getChampionName(m.championPickIntent()));
                }
            }

            if (session.bans() != null) {
                for (LcuBanDTO b : session.bans().myTeamBans()) {
                    if (previous == null || !hasBan(previous.bans().myTeamBans(), b.championId())) {
                        System.out.println(">>> [WS] Equipo azul baneó a " + lcuApi.getChampionName(b.championId()));
                    }
                }
                for (LcuBanDTO b : session.bans().theirTeamBans()) {
                    if (previous == null || !hasBan(previous.bans().theirTeamBans(), b.championId())) {
                        System.out.println(">>> [WS] Equipo rojo baneó a " + lcuApi.getChampionName(b.championId()));
                    }
                }
            }

            lcuApi.updateChampSelect(session);

        } catch (Exception e) {
            System.out.println(">>> [WS] Error parseando: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    private boolean hasPickIntent(List<LcuTeamMemberDTO> team, int cellId, int intent) {
        return team.stream().anyMatch(m -> m.cellId() == cellId && m.championPickIntent() == intent);
    }

    private boolean hasBan(List<LcuBanDTO> bans, long championId) {
        return bans.stream().anyMatch(b -> b.championId() == championId);
    }

    private SSLContext createSslContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
            }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new SecureRandom());
        return sslContext;
    }

    public synchronized void disconnect() {
        shouldStop = true;
        if (client != null && client.isOpen()) {
            client.close();
        }
    }

    public boolean isOpen() {
        return client != null && client.isOpen();
    }

    @PreDestroy
    public void cleanup() {
        disconnect();
    }
}
