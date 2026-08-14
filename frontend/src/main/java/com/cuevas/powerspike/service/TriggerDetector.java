package com.cuevas.powerspike.service;

import com.cuevas.powerspike.analysis.*;
import com.cuevas.powerspike.dto.*;
import com.cuevas.powerspike.service.analysis.AnalysisApiClient;
import com.cuevas.powerspike.service.analysis.ScreenshotService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Detecta CUÁNDO disparar cada análisis de IA (reemplaza a la vieja
 * AnalysisEngine, que vivía en el mismo proceso que el backend). Esta clase
 * vive en el frontend porque ya está escuchando los datos locales del juego
 * (LCU + Live Client) en tiempo real, con la latencia más baja posible.
 *
 * Cuando detecta un trigger, arma el request correspondiente (DTOs de
 * `common`) y se lo pasa a AnalysisApiClient, que llama al backend por HTTP.
 * El backend arma el prompt y llama a OpenAI; acá no hay lógica de IA.
 */
@Service
public class TriggerDetector {

    private static final long MIN_INTERVAL_MS = 30_000; // Cooldown mínimo entre análisis de muerte

    private final GameStateService gameStateService;
    private final AnalysisApiClient analysisApiClient;
    private final ScreenshotService screenshotService;
    private final BackendApiClient backendApiClient;

    private String previousPhase = "CLOSED";
    private int lastDeathEventId = -1;
    private long lastAnalysisTime = 0;

    private double lastDragonKill = -1;
    private double lastHeraldKill = -1;
    private double lastBaronKill = -1;
    private double lastHordeKill = -1;
    private double lastObjectiveCheck = 0;
    private double lastAlertedSpawn = -1;

    public TriggerDetector(GameStateService gameStateService,
                           AnalysisApiClient analysisApiClient,
                           ScreenshotService screenshotService,
                           BackendApiClient backendApiClient) {
        this.gameStateService = gameStateService;
        this.analysisApiClient = analysisApiClient;
        this.screenshotService = screenshotService;
        this.backendApiClient = backendApiClient;
    }

    @PostConstruct
    public void init() {
        gameStateService.gamePhaseProperty().addListener((obs, oldVal, newVal) -> onPhaseChange(oldVal, newVal));

        gameStateService.liveGameDataProperty().addListener((obs, oldVal, newVal) -> {
            onLiveGameUpdate(oldVal, newVal);
            if (oldVal == null && newVal != null && newVal.activePlayer() != null) {
                triggerLiveClientMatchup(newVal);
            }
        });
    }

    private void onPhaseChange(String oldPhase, String newPhase) {
        if (oldPhase == null) oldPhase = "CLOSED";
        if (newPhase == null) newPhase = "CLOSED";

        // Nueva partida en formación: resetear todo el estado de triggers de la
        // partida anterior. Sin esto, la 2da partida no dispara muertes ni
        // objetivos porque lastDeathEventId y los timers quedan con valores viejos
        // (los EventIDs del Live Client se numeran por partida).
        if ("ChampSelect".equals(newPhase)) {
            resetForNewGame();
        }

        if ("InProgress".equals(oldPhase) && !"InProgress".equals(newPhase)) {
            LiveClientAllDataDTO liveData = gameStateService.getLiveGameData();
            if (liveData != null && liveData.activePlayer() != null) {
                triggerGameEnd(liveData);
            }
        }

        previousPhase = newPhase;
    }

    /**
     * Reinicia el estado de detección de triggers. Se llama al entrar a ChampSelect
     * (nueva partida). Es importante no resetear al salir de InProgress porque el
     * análisis de game-end usa los datos actuales.
     */
    private void resetForNewGame() {
        lastDeathEventId = -1;
        lastAnalysisTime = 0;
        lastDragonKill = -1;
        lastHeraldKill = -1;
        lastBaronKill = -1;
        lastHordeKill = -1;
        lastObjectiveCheck = 0;
        lastAlertedSpawn = -1;
    }

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
                    triggerDeath(newData, event);
                }
                break;
            }
        }

        for (LiveClientEventDTO event : events) {
            String eventName = event.EventName();
            if ("DragonKill".equals(eventName)) lastDragonKill = event.EventTime();
            else if ("HeraldKill".equals(eventName)) lastHeraldKill = event.EventTime();
            else if ("BaronKill".equals(eventName)) lastBaronKill = event.EventTime();
            else if ("HordeKill".equals(eventName)) lastHordeKill = event.EventTime();
        }

        if (!events.isEmpty()) {
            double gameTime = events.getLast().EventTime();
            if (gameTime - lastObjectiveCheck >= 10 && !previousPhase.equals("CLOSED")) {
                lastObjectiveCheck = gameTime;
                checkUpcomingObjectives(newData, gameTime);
            }
        }
    }

    private void checkUpcomingObjectives(LiveClientAllDataDTO data, double gameTime) {
        checkSpawn(data, gameTime, lastDragonKill, 300, 300, "Dragón");
        checkSpawn(data, gameTime, lastHeraldKill, 840, 360, "Heraldo");
        checkSpawn(data, gameTime, lastBaronKill, 1200, 360, "Barón");

        if (lastHordeKill < 0 && gameTime >= 330 && gameTime <= 335) {
            if (lastAlertedSpawn < 330) {
                lastAlertedSpawn = 330;
                triggerObjectiveSpawn(data, "Larvas", 360, gameTime);
            }
        }
    }

    private void checkSpawn(LiveClientAllDataDTO data, double gameTime, double lastKill,
                            double firstSpawn, double respawn, String name) {
        double nextSpawn = lastKill < 0 ? firstSpawn : lastKill + respawn;
        if (gameTime >= nextSpawn - 35 && gameTime <= nextSpawn - 25) {
            if (lastAlertedSpawn < nextSpawn - 30) {
                lastAlertedSpawn = nextSpawn - 30;
                triggerObjectiveSpawn(data, name, nextSpawn, gameTime);
            }
        }
    }

    private boolean isMyDeath(LiveClientEventDTO event, String myName) {
        if (!"ChampionKill".equals(event.EventName())) return false;
        if (event.VictimName() == null || myName == null) return false;

        String victimName = event.VictimName();
        String myBaseName = myName.contains("#") ? myName.substring(0, myName.indexOf("#")) : myName;

        return victimName.equals(myBaseName) || victimName.equals(myName) || victimName.contains(myBaseName);
    }

    // ---- disparadores ----

    private void triggerChampSelect(LcuChampSelectDTO cs) {
        String myGameName = gameStateService.getMyGameName();
        String myTagLine = gameStateService.getMyTagLine();
        String myName = (myGameName != null && !myGameName.isEmpty())
                ? myGameName + "#" + myTagLine
                : "Jugador";

        String myChamp = findMyChampion(cs);
        String myRole = findMyRole(cs);
        String enemyChamp = findEnemyInMyLane(cs, myRole);

        gameStateService.setMyRole(myRole);

        analysisApiClient.analyzeChampSelect(new ChampSelectAnalysisRequest(
                cs, null, myName, myChamp, myRole, enemyChamp, myRole));
    }

    private void triggerLiveClientMatchup(LiveClientAllDataDTO data) {
        analysisApiClient.analyzeMatchup(new LiveClientMatchupAnalysisRequest(data));
    }

    private void triggerDeath(LiveClientAllDataDTO data, LiveClientEventDTO deathEvent) {
        lastAnalysisTime = System.currentTimeMillis();
        String myRole = gameStateService.getMyRole();

        String screenshot = screenshotService.captureScreenAsBase64();
        analysisApiClient.analyzeDeath(new DeathAnalysisRequest(data, deathEvent, myRole, screenshot));
    }

    private void triggerObjectiveSpawn(LiveClientAllDataDTO data, String objective, double spawnTime, double gameTime) {
        analysisApiClient.analyzeObjectiveSpawn(new ObjectiveSpawnAnalysisRequest(data, objective, spawnTime, gameTime));
    }

    private void triggerGameEnd(LiveClientAllDataDTO data) {
        String myChamp = data.activePlayer() != null ? data.activePlayer().championName() : "desconocido";
        String myName = data.activePlayer() != null ? data.activePlayer().summonerName() : "Jugador";
        analysisApiClient.analyzeGameEnd(new GameEndAnalysisRequest(data, myChamp, myName));
    }

    // ---- helpers de champ select (resuelven nombres de campeón vía backend) ----

    private String findMyChampion(LcuChampSelectDTO cs) {
        if (cs.myTeam() == null) return "desconocido";

        String myGameName = gameStateService.getMyGameName();
        if (myGameName != null && !myGameName.isEmpty()) {
            return cs.myTeam().stream()
                    .filter(m -> m.gameName() != null && m.gameName().equalsIgnoreCase(myGameName))
                    .findFirst()
                    .map(this::resolveChampionName)
                    .orElse("desconocido");
        }

        String activeName = gameStateService.getActivePlayerName();
        if (activeName != null && !activeName.isEmpty()) {
            String baseName = activeName.contains("#") ? activeName.substring(0, activeName.indexOf("#")) : activeName;
            return cs.myTeam().stream()
                    .filter(m -> m.gameName() != null && m.gameName().contains(baseName))
                    .findFirst()
                    .map(this::resolveChampionName)
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

    private String findEnemyInMyLane(LcuChampSelectDTO cs, String myRole) {
        if (cs.theirTeam() == null || myRole == null) return null;
        return cs.theirTeam().stream()
                .filter(m -> myRole.equals(m.assignedPosition()))
                .filter(m -> m.puuid() != null && !m.puuid().isEmpty())
                .findFirst()
                .map(this::resolveChampionName)
                .orElse(null);
    }

    private String resolveChampionName(LcuTeamMemberDTO m) {
        int champId = m.championId() > 0 ? m.championId() : m.championPickIntent();
        if (champId <= 0) return "sin campeón";
        String name = backendApiClient.getChampionName(champId);
        return name != null ? name : "Champion " + champId;
    }
}
