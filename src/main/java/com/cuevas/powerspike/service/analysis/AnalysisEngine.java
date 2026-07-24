package com.cuevas.powerspike.service.analysis;

import com.cuevas.powerspike.dto.*;
import com.cuevas.powerspike.service.DataDragonClient;
import com.cuevas.powerspike.service.GameStateService;
import jakarta.annotation.PostConstruct;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Motor de análisis con IA.
 *
 * Escucha los cambios del GameStateService y dispara análisis automáticos en 3 momentos:
 *   1. CHAMP_SELECT_END: cuando termina la selección de campeones → analiza matchup
 *   2. DEATH: cuando el jugador muere → consejo rápido (con cooldown de 30s)
 *   3. GAME_END: cuando termina la partida → análisis post-game completo
 *
 * Los análisis se ejecutan en un hilo separado para no bloquear la UI.
 * El resultado se publica via una JavaFX Property observable que el MainController consume.
 */
@Service
public class AnalysisEngine {

    private final GameStateService gameStateService;
    private final PromptBuilder promptBuilder;
    private final OpenAIClient openAIClient;
    private final TtsClient ttsClient;
    private final DataDragonClient dataDragonClient;

    // Hilo único para análisis secuenciales (evita race conditions con el cooldown)
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Property observable: cuando cambia, la UI se actualiza automáticamente
    private final ObjectProperty<AnalysisResult> latestResult = new SimpleObjectProperty<>();

    // Estado para detectar triggers sin repetir
    private String previousPhase = "CLOSED";
    private int lastDeathEventId = -1;          // ID del último evento de muerte procesado
    private long lastAnalysisTime = 0;          // Timestamp del último análisis (para cooldown)
    private static final long MIN_INTERVAL_MS = 30_000; // Cooldown mínimo entre análisis

    public AnalysisEngine(GameStateService gameStateService,
                          PromptBuilder promptBuilder,
                          OpenAIClient openAIClient,
                          TtsClient ttsClient,
                          DataDragonClient dataDragonClient) {
        this.gameStateService = gameStateService;
        this.promptBuilder = promptBuilder;
        this.openAIClient = openAIClient;
        this.ttsClient = ttsClient;
        this.dataDragonClient = dataDragonClient;
    }

    /**
     * Se registra al iniciar el servicio. Se suscribe a los listeners del GameStateService
     * para reaccionar a cambios de fase y datos de la partida en tiempo real.
     */
    @PostConstruct
    public void init() {
        // Cambio de fase → detecta fin de champ select y fin de partida
        gameStateService.gamePhaseProperty().addListener((obs, oldVal, newVal) -> {
            onPhaseChange(oldVal, newVal);
        });

        // Actualización de datos en vivo → detecta muertes y primera conexión del Live Client
        gameStateService.liveGameDataProperty().addListener((obs, oldVal, newVal) -> {
            onLiveGameUpdate(oldVal, newVal);
            // Primeros datos del Live Client → análisis concreto de matchup
            if (oldVal == null && newVal != null && newVal.activePlayer() != null) {
                triggerLiveClientMatchupAnalysis(newVal);
            }
        });
    }

    /**
     * Detecta transiciones de fase relevantes:
     *   ChampSelect → cualquier otra: fin del champ select, armar análisis de matchup
     *   InProgress → cualquier otra: fin de partida, armar análisis post-game
     */
    private void onPhaseChange(String oldPhase, String newPhase) {
        if (oldPhase == null) oldPhase = "CLOSED";
        if (newPhase == null) newPhase = "CLOSED";

        if ("ChampSelect".equals(oldPhase) && !"ChampSelect".equals(newPhase)) {
            LcuChampSelectDTO cs = gameStateService.getChampSelect();
            if (cs != null) {
                triggerChampSelectAnalysis(cs);
            }
        }

        if ("InProgress".equals(oldPhase) && !"InProgress".equals(newPhase)) {
            LiveClientAllDataDTO liveData = gameStateService.getLiveGameData();
            if (liveData != null) {
                triggerGameEndAnalysis(liveData);
            }
        }

        previousPhase = newPhase;
    }

    /**
     * Recorre los eventos de la partida buscando muertes del jugador.
     * Usa EventID para no procesar muertes ya analizadas.
     * Aplica cooldown para evitar spamear análisis en muertes seguidas.
     */
    private void onLiveGameUpdate(LiveClientAllDataDTO oldData, LiveClientAllDataDTO newData) {
        if (newData == null || newData.events() == null || newData.events().Events() == null) return;
        if (newData.activePlayer() == null) return;

        String myName = newData.activePlayer().summonerName();
        List<LiveClientEventDTO> events = newData.events().Events();

        for (LiveClientEventDTO event : events) {
            if (isMyDeath(event, myName) && event.EventID() > lastDeathEventId) {
                lastDeathEventId = event.EventID();

                long now = System.currentTimeMillis();
                if (now - lastAnalysisTime >= MIN_INTERVAL_MS) {
                    triggerDeathAnalysis(newData, event);
                }
                break; // Solo procesar la primera muerte nueva
            }
        }
    }

    /**
     * Verifica si un evento de ChampionKill es una muerte del jugador.
     * Compara el VictimName del evento con el summonerName del jugador,
     * ignorando el tagline (#314) porque la API de Live Client no lo incluye.
     */
    private boolean isMyDeath(LiveClientEventDTO event, String myName) {
        if (!"ChampionKill".equals(event.EventName())) return false;
        if (event.VictimName() == null || myName == null) return false;

        String victimName = event.VictimName();
        String myBaseName = myName.contains("#") ? myName.substring(0, myName.indexOf("#")) : myName;

        return victimName.equals(myBaseName) || victimName.equals(myName) || victimName.contains(myBaseName);
    }

    /**
     * Dispara análisis de champ select.
     * Extrae: campeón propio, rol, enemigo de la misma línea.
     * Identifica al jugador por puuid (no por "el primero con campeón confirmado").
     */
    private void triggerChampSelectAnalysis(LcuChampSelectDTO cs) {
        if (!openAIClient.isConfigured()) return;

        System.out.println(">>> [DEBUG CS] === INICIO triggerChampSelectAnalysis ===");
        System.out.println(">>> [DEBUG CS] cs.myTeam.size: " + (cs.myTeam() != null ? cs.myTeam().size() : "null"));
        System.out.println(">>> [DEBUG CS] cs.theirTeam.size: " + (cs.theirTeam() != null ? cs.theirTeam().size() : "null"));
        System.out.println(">>> [DEBUG CS] cs.bans: " + cs.bans());

        // Usar el nombre guardado del invocador, no el del LiveClient (no existe en champ select)
        String myGameName = gameStateService.getMyGameName();
        String myTagLine = gameStateService.getMyTagLine();
        String myName = (myGameName != null && !myGameName.isEmpty())
                ? myGameName + "#" + myTagLine
                : "Jugador";

        String myPuuid = gameStateService.getMyPuuid();
        System.out.println(">>> [DEBUG CS] Mi puuid guardado: '" + myPuuid + "'");
        System.out.println(">>> [DEBUG CS] Mi nombre (live): " + myName);

        // DEBUG: Mostrar datos crudos del champ select
        System.out.println(">>> [DEBUG CS RAW] myTeam completo:");
        if (cs.myTeam() != null) {
            for (LcuTeamMemberDTO m : cs.myTeam()) {
                System.out.println(">>> [DEBUG CS RAW]   puuid='" + m.puuid() + "' champId=" + m.championId()
                        + " pickIntent=" + m.championPickIntent() + " pos=" + m.assignedPosition()
                        + " name=" + m.gameName() + "#" + m.tagLine()
                        + " summonerId=" + m.summonerId());
            }
        }
        System.out.println(">>> [DEBUG CS RAW] theirTeam completo:");
        if (cs.theirTeam() != null) {
            for (LcuTeamMemberDTO m : cs.theirTeam()) {
                System.out.println(">>> [DEBUG CS RAW]   puuid='" + m.puuid() + "' champId=" + m.championId()
                        + " pickIntent=" + m.championPickIntent() + " pos=" + m.assignedPosition()
                        + " name=" + m.gameName() + "#" + m.tagLine()
                        + " summonerId=" + m.summonerId());
            }
        }

        String myChamp = findMyChampion(cs);
        String myRole = findMyRole(cs);
        String enemyChamp = findEnemyInMyLane(cs, myRole);
        String enemyRole = myRole;

        // Guardar el rol para usarlo en análisis posteriores (muertes, post-game)
        gameStateService.setMyRole(myRole);

        System.out.println(">>> [DEBUG CS] Mi campeón: " + myChamp);
        System.out.println(">>> [DEBUG CS] Mi rol: " + myRole);
        System.out.println(">>> [DEBUG CS] Enemigo: " + enemyChamp);
        System.out.println(">>> [DEBUG CS] Rol enemigo: " + enemyRole);

        AnalysisContext ctx = AnalysisContext.champSelect(cs, myChamp, myRole, enemyChamp, enemyRole, myName);
        String prompt = promptBuilder.buildChampSelectPrompt(ctx);
        System.out.println(">>> [DEBUG CS] Prompt:\n" + prompt);
        System.out.println(">>> [DEBUG CS] --- FIN PROMPT ---");

        runAnalysis(ctx, prompt);
    }

    /**
     * Dispara análisis de muerte con contexto completo:
     * posición en el mapa, visión, asistentes del killer, comparación stats.
     */
    private void triggerDeathAnalysis(LiveClientAllDataDTO data, LiveClientEventDTO deathEvent) {
        if (!openAIClient.isConfigured()) return;

        String myChamp = data.activePlayer().championName();
        String myName = data.activePlayer().summonerName();
        String myRole = gameStateService.getMyRole();
        int minute = (int) (deathEvent.EventTime() / 60.0);

        // Buscar mi player en allPlayers para obtener team y stats
        LiveClientPlayerDTO myPlayer = data.allPlayers().stream()
                .filter(p -> p.summonerName().equals(myName) || p.championName().equals(myChamp))
                .findFirst().orElse(null);
        String myTeam = myPlayer != null ? myPlayer.team() : "ORDER";

        // Buscar al killer en allPlayers
        LiveClientPlayerDTO killer = data.allPlayers().stream()
                .filter(p -> deathEvent.KillerName() != null && 
                    (p.summonerName().equals(deathEvent.KillerName()) || p.championName().equals(deathEvent.KillerName())))
                .findFirst().orElse(null);

        // Clasificar zona de muerte
        String deathZone = "desconocida";
        boolean hasVision = false;
        if (deathEvent.position() != null) {
            MapZoneClassifier.Zone zone = MapZoneClassifier.classify(
                    deathEvent.position().x(), deathEvent.position().y(), myTeam);
            deathZone = zone.label;
            hasVision = MapZoneClassifier.hasNearbyVision(data.events(), deathEvent.EventTime(), deathEvent.position());
        }

        // Contexto de asistentes: 1v1 vs gank
        int assisters = deathEvent.Assisters() != null ? deathEvent.Assisters().size() : 0;
        String fightType = assisters == 0 ? "1v1 (duelo individual)"
                : assisters == 1 ? "1v2 (ganked)"
                : "1v" + (assisters + 1) + " (ganked o team fight)";

        String assistersList = "";
        if (deathEvent.Assisters() != null && !deathEvent.Assisters().isEmpty()) {
            assistersList = "Asistentes del killer: " + String.join(", ", deathEvent.Assisters());
        }

        // Comparación con el killer
        String killerComparison = "";
        if (killer != null && myPlayer != null) {
            int levelDiff = killer.level() - myPlayer.level();
            int csDiff = (killer.scores() != null ? killer.scores().creepScore() : 0)
                    - (myPlayer.scores() != null ? myPlayer.scores().creepScore() : 0);
            killerComparison = String.format("Killer: Lv.%d (%s%d), %s%d CS vs vos. %s",
                    killer.level(),
                    levelDiff > 0 ? "+" : "", levelDiff,
                    csDiff > 0 ? "+" : "", csDiff,
                    levelDiff > 0 ? "Te supera en nivel." : "Estás igual o mejor en nivel.");
        }

        String prompt = promptBuilder.buildDeathPrompt(data, deathEvent, myRole, deathZone, 
                hasVision, fightType, killerComparison, assistersList);
        
        AnalysisContext ctx = AnalysisContext.death(data, deathEvent, myChamp, myName, minute);
        runAnalysis(ctx, prompt);
    }

    /**
     * Busca eventos ocurridos 30 segundos antes y después de la muerte.
     * Ayuda a contextualizar si la muerte fue en una team fight, objetivo, etc.
     */
    private String findNearbyEvents(LiveClientEventsDTO events, double deathTime) {
        if (events == null || events.Events() == null) return "sin eventos cercanos";

        List<LiveClientEventDTO> nearby = events.Events().stream()
                .filter(e -> Math.abs(e.EventTime() - deathTime) <= 30.0)
                .filter(e -> !"ChampionKill".equals(e.EventName())) // Excluir otras muertes
                .limit(3)
                .toList();

        if (nearby.isEmpty()) return "sin eventos cercanos";

        return nearby.stream()
                .map(e -> e.EventName() + " (min " + (int)(e.EventTime()/60) + ")")
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * Análisis concreto de matchup cuando el Live Client conecta.
     * A diferencia del champ select (especulativo), acá tenemos roles exactos de los 10 jugadores.
     */
    private void triggerLiveClientMatchupAnalysis(LiveClientAllDataDTO data) {
        if (!openAIClient.isConfigured()) return;

        String myName = data.activePlayer().summonerName();

        // Buscar mi jugador y el enemigo de la misma línea
        LiveClientPlayerDTO myPlayer = data.allPlayers().stream()
                .filter(p -> p.summonerName().equals(myName) || p.championName().equals(data.activePlayer().championName()))
                .findFirst().orElse(null);

        if (myPlayer == null) return;

        String myRole = myPlayer.position();
        String myTeam = myPlayer.team();
        gameStateService.setMyRole(myRole);

        // Buscar enemigo de la misma posición en el equipo contrario
        LiveClientPlayerDTO enemyPlayer = data.allPlayers().stream()
                .filter(p -> !p.team().equals(myTeam))
                .filter(p -> myRole != null && myRole.equals(p.position()))
                .findFirst().orElse(null);

        String enemyChamp = enemyPlayer != null ? enemyPlayer.championName() : "desconocido";

        // Guardar el enemigo del matchup para usarlo en análisis de muertes
        gameStateService.setMyEnemyChampion(enemyChamp);
        gameStateService.setMyEnemyName(enemyPlayer != null ? enemyPlayer.summonerName() : null);

        String prompt = promptBuilder.buildLiveClientMatchupPrompt(data, myPlayer, enemyPlayer);
        runAnalysis(AnalysisTrigger.LIVE_CLIENT_MATCHUP, prompt);
    }
    private void triggerGameEndAnalysis(LiveClientAllDataDTO data) {
        if (!openAIClient.isConfigured()) return;

        String myChamp = data.activePlayer() != null ? data.activePlayer().championName() : "desconocido";
        String myName = data.activePlayer() != null ? data.activePlayer().summonerName() : "Jugador";

        AnalysisContext ctx = AnalysisContext.gameEnd(data, myChamp, myName);
        runAnalysis(ctx, promptBuilder.buildGameEndPrompt(ctx));
    }

    private void runAnalysis(AnalysisTrigger trigger, String prompt) {
        executor.submit(() -> {
            lastAnalysisTime = System.currentTimeMillis();
            String response = openAIClient.chat(prompt);
            AnalysisResult result = AnalysisResult.success(trigger, prompt, response);
            Platform.runLater(() -> latestResult.set(result));
            if (ttsClient.isConfigured() && response != null && !response.startsWith("[Error]")) {
                ttsClient.speak(response, "Speak in a friendly and encouraging tone, like a coach giving advice.");
            }
        });
    }

    private void runAnalysis(AnalysisContext ctx, String prompt) {
        runAnalysis(ctx.trigger(), prompt);
    }

    /**
     * Busca al jugador en el equipo por puuid y resuelve su nombre de campeón.
     * Usa championId si está confirmado, sino championPickIntent (el campeón en hover).
     */
    private String findMyChampion(LcuChampSelectDTO cs) {
        if (cs.myTeam() == null) return "desconocido";
        
        // Buscar por nombre (RiotId guardado en GameStateService)
        String myGameName = gameStateService.getMyGameName();
        String myTagLine = gameStateService.getMyTagLine();
        
        if (myGameName != null && !myGameName.isEmpty()) {
            return cs.myTeam().stream()
                    .filter(m -> m.gameName() != null && m.gameName().equalsIgnoreCase(myGameName))
                    .findFirst()
                    .map(m -> resolveChampionName(m))
                    .orElse("desconocido");
        }
        
        // Fallback: buscar por nombre del active player
        String activeName = gameStateService.getActivePlayerName();
        if (activeName != null && !activeName.isEmpty()) {
            String baseName = activeName.contains("#") ? activeName.substring(0, activeName.indexOf("#")) : activeName;
            return cs.myTeam().stream()
                    .filter(m -> m.gameName() != null && m.gameName().contains(baseName))
                    .findFirst()
                    .map(m -> resolveChampionName(m))
                    .orElse("desconocido");
        }
        
        return "desconocido";
    }

    private String findMyRole(LcuChampSelectDTO cs) {
        if (cs.myTeam() == null) return "desconocido";
        
        String myGameName = gameStateService.getMyGameName();
        
        if (myGameName != null && !myGameName.isEmpty()) {
            return cs.myTeam().stream()
                    .filter(m -> m.gameName() != null && m.gameName().equalsIgnoreCase(myGameName))
                    .findFirst()
                    .map(m -> m.assignedPosition() != null ? m.assignedPosition() : "desconocido")
                    .orElse("desconocido");
        }
        
        return "desconocido";
    }

    /**
     * Busca el enemigo asignado a la misma línea (top, jungle, mid, bot, utility).
     */
    private String findEnemyInMyLane(LcuChampSelectDTO cs, String myRole) {
        if (cs.theirTeam() == null || myRole == null) return null;
        
        // Buscar enemigo con la misma posición, ignorando bots (puuid vacío)
        return cs.theirTeam().stream()
                .filter(m -> myRole.equals(m.assignedPosition()))
                .filter(m -> m.puuid() != null && !m.puuid().isEmpty()) // Ignorar bots
                .findFirst()
                .map(m -> resolveChampionName(m))
                .orElse(null);
    }

    /**
     * Resuelve el nombre del campeón de un miembro del equipo.
     * Prioriza championId (confirmado), fallback a championPickIntent (hover).
     * Si ninguno está disponible, devuelve "sin campeón".
     */
    private String resolveChampionName(LcuTeamMemberDTO m) {
        int champId = m.championId() > 0 ? m.championId() : m.championPickIntent();
        if (champId <= 0) return "sin campeón";
        String name = dataDragonClient.getChampionName(champId);
        return name != null ? name : "Champion " + champId;
    }

    public ObjectProperty<AnalysisResult> latestResultProperty() { return latestResult; }
    public AnalysisResult getLatestResult() { return latestResult.get(); }
}
