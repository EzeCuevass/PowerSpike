package com.cuevas.powerspike.service;

import com.cuevas.powerspike.dto.LcuChampSelectDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LcuApi {

    private final RestTemplate lcuRestTemplate;
    private final LcuLockfile lcuLockfile;
    private final LcuWebSocket lcuWebSocket;
    private final DataDragonClient dataDragonClient;

    private String currentPhase = "CLOSED";
    private LcuChampSelectDTO currentChampSelect;
    private String champSelectError;

    public LcuApi(@Qualifier("lcuRestTemplate") RestTemplate lcuRestTemplate,
                  LcuLockfile lcuLockfile,
                  LcuWebSocket lcuWebSocket,
                  DataDragonClient dataDragonClient) {
        this.lcuRestTemplate = lcuRestTemplate;
        this.lcuLockfile = lcuLockfile;
        this.lcuWebSocket = lcuWebSocket;
        this.dataDragonClient = dataDragonClient;
    }

    @Scheduled(fixedDelay = 2000)
    public void pollPhase() {
        lcuLockfile.check();
        if (!lcuLockfile.isConnected()) {
            currentPhase = "CLOSED";
            currentChampSelect = null;
            return;
        }
        try {
            String phase = getPhase();
            if (phase != null && !phase.equals(currentPhase)) {
                System.out.println(">>> Fase cambiada: " + phase);
                currentPhase = phase;

                if ("ChampSelect".equals(phase)) {
                    System.out.println(">>> [WS] Conectando WebSocket para champ select...");
                    lcuWebSocket.connectToLCU();
                } else if (lcuWebSocket.isOpen()) {
                    System.out.println(">>> [WS] Cerrando WebSocket (fase: " + phase + ")");
                    lcuWebSocket.disconnect();
                }
            }

            if (!"ChampSelect".equals(currentPhase)) {
                currentChampSelect = null;
                champSelectError = null;
            }

        } catch (Exception e) {
            currentPhase = "ERROR";
        }
    }

    public String getPhase() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", lcuLockfile.getAuth());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = "https://127.0.0.1:" + lcuLockfile.getPort()
                + "/lol-gameflow/v1/gameflow-phase";

        try {
            ResponseEntity<String> response = lcuRestTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class
            );
            String phase = response.getBody();
            if (phase != null) {
                phase = phase.replace("\"", "");
            }
            return phase;
        } catch (Exception e) {
            System.out.println(">>> LCU getPhase error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }

    public String refreshPhase() {
        lcuLockfile.check();
        if (!lcuLockfile.isConnected()) {
            currentPhase = "CLOSED";
            return "CLOSED (no lockfile)";
        }
        String phase = getPhase();
        if (phase != null) {
            currentPhase = phase;
            return phase;
        }
        return currentPhase + " (getPhase devolvió null)";
    }

    public void loadChampSelect() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", lcuLockfile.getAuth());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = "https://127.0.0.1:" + lcuLockfile.getPort()
                + "/lol-champ-select/v1/session";

        try {
            ResponseEntity<LcuChampSelectDTO> response = lcuRestTemplate.exchange(
                    url, HttpMethod.GET, entity, LcuChampSelectDTO.class
            );
            currentChampSelect = response.getBody();
            champSelectError = null;
        } catch (Exception e) {
            champSelectError = e.getClass().getSimpleName() + " - " + e.getMessage();
            currentChampSelect = null;
        }
    }

    public String refreshChampSelect() {
        champSelectError = null;
        currentChampSelect = null;
        loadChampSelect();
        if (currentChampSelect != null) return "OK";
        return champSelectError != null ? champSelectError : "null";
    }

    public void updateChampSelect(LcuChampSelectDTO data) {
        this.currentChampSelect = data;
    }

    public String getChampionName(long championId) {
        String name = dataDragonClient.getChampionName(championId);
        return name != null ? name : "Champion_" + championId;
    }

    public String getChampSelectError() { return champSelectError; }
    public String getCurrentPhase() { return currentPhase; }
    public LcuChampSelectDTO getCurrentChampSelect() { return currentChampSelect; }
    public boolean isInChampSelect() { return "ChampSelect".equals(currentPhase); }
}
