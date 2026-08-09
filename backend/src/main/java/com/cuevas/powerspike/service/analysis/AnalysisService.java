package com.cuevas.powerspike.service.analysis;

import com.cuevas.powerspike.analysis.*;
import com.cuevas.powerspike.dto.LiveClientAllDataDTO;
import com.cuevas.powerspike.dto.LiveClientEventDTO;
import com.cuevas.powerspike.dto.LiveClientPlayerDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

/**
 * Motor de análisis con IA. Reemplaza a la vieja AnalysisEngine (que vivía en el
 * proceso monolítico y escuchaba cambios de GameStateService). Ahora es un
 * servicio "stateless": el frontend detecta cuándo disparar cada análisis
 * (tiene los datos locales del juego) y llama a uno de los 5 métodos de acá,
 * pasando el contexto crudo necesario. Este servicio arma el prompt
 * (PromptBuilder), llama a OpenAI (GPT Luna 5.6, multimodal) y opcionalmente
 * genera el audio TTS, devolviendo todo en un AnalysisResponse.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final PromptBuilder promptBuilder;
    private final OpenAIClient openAIClient;
    private final TtsClient ttsClient;

    public AnalysisService(PromptBuilder promptBuilder, OpenAIClient openAIClient, TtsClient ttsClient) {
        this.promptBuilder = promptBuilder;
        this.openAIClient = openAIClient;
        this.ttsClient = ttsClient;
    }

    public AnalysisResponse analyzeChampSelect(ChampSelectAnalysisRequest req) {
        if (!openAIClient.isConfigured()) {
            return AnalysisResponse.error(AnalysisTrigger.CHAMP_SELECT_END, "OpenAI no configurado en el backend.");
        }

        String prompt;
        if (req.champSelect() != null) {
            AnalysisContext ctx = AnalysisContext.champSelect(req.champSelect(), req.myChampion(), req.myRole(),
                    req.enemyChampion(), req.enemyRole(), req.mySummonerName());
            prompt = promptBuilder.buildChampSelectPrompt(ctx);
        } else if (req.liveClientData() != null) {
            LiveClientPlayerDTO myPlayer = findMyPlayer(req.liveClientData());
            if (myPlayer == null) {
                return AnalysisResponse.error(AnalysisTrigger.CHAMP_SELECT_END, "No se pudo identificar al jugador en los datos recibidos.");
            }
            prompt = promptBuilder.buildChampSelectFromLiveClient(req.liveClientData(), myPlayer);
        } else {
            return AnalysisResponse.error(AnalysisTrigger.CHAMP_SELECT_END, "No se recibió champSelect ni liveClientData.");
        }

        return runChatAnalysis(AnalysisTrigger.CHAMP_SELECT_END, prompt);
    }

    public AnalysisResponse analyzeLiveClientMatchup(LiveClientMatchupAnalysisRequest req) {
        if (!openAIClient.isConfigured()) {
            return AnalysisResponse.error(AnalysisTrigger.LIVE_CLIENT_MATCHUP, "OpenAI no configurado en el backend.");
        }

        LiveClientAllDataDTO data = req.data();
        LiveClientPlayerDTO myPlayer = findMyPlayer(data);
        if (myPlayer == null) {
            return AnalysisResponse.error(AnalysisTrigger.LIVE_CLIENT_MATCHUP, "No se pudo identificar al jugador en los datos recibidos.");
        }

        String myRole = myPlayer.position();
        String myTeam = myPlayer.team();
        LiveClientPlayerDTO enemyPlayer = data.allPlayers().stream()
                .filter(p -> !p.team().equals(myTeam))
                .filter(p -> myRole != null && myRole.equals(p.position()))
                .findFirst().orElse(null);

        String prompt = promptBuilder.buildLiveClientMatchupPrompt(data, myPlayer, enemyPlayer);
        return runChatAnalysis(AnalysisTrigger.LIVE_CLIENT_MATCHUP, prompt);
    }

    public AnalysisResponse analyzeDeath(DeathAnalysisRequest req) {
        if (!openAIClient.isConfigured()) {
            return AnalysisResponse.error(AnalysisTrigger.DEATH, "OpenAI no configurado en el backend.");
        }

        LiveClientAllDataDTO data = req.data();
        LiveClientEventDTO deathEvent = req.deathEvent();
        String myName = data.activePlayer().summonerName();

        LiveClientPlayerDTO myPlayer = findMyPlayer(data);
        String myTeam = myPlayer != null ? myPlayer.team() : "ORDER";

        LiveClientPlayerDTO killer = data.allPlayers().stream()
                .filter(p -> deathEvent.KillerName() != null &&
                        (p.summonerName().equals(deathEvent.KillerName()) ||
                         p.championName().equals(deathEvent.KillerName())))
                .findFirst().orElse(null);

        boolean hasVision = false;
        String deathZone = "desconocida";
        if (deathEvent.Position() != null) {
            MapZoneClassifier.Zone zone = MapZoneClassifier.classify(
                    deathEvent.Position().x(), deathEvent.Position().y(), myTeam);
            deathZone = zone.label;
            hasVision = MapZoneClassifier.hasNearbyVision(data.events(), deathEvent.EventTime(), deathEvent.Position());
        }

        int recentWards = countRecentWards(data.events(), deathEvent.EventTime());
        String visionText;
        if (hasVision) {
            visionText = "Habías colocado visión en la zona.";
        } else if (recentWards > 0) {
            visionText = "Colocaste " + recentWards + " ward(s) recientemente pero no se sabe si cubrían esta zona.";
        } else {
            visionText = "NO wardaste en los últimos 2 minutos. Probablemente estabas sin visión.";
        }

        int assisters = deathEvent.Assisters() != null ? deathEvent.Assisters().size() : 0;
        String fightType = assisters == 0 ? "Te mató solo el killer"
                : assisters == 1 ? "Te mataron entre 2 enemigos"
                : "Te mataron entre " + (assisters + 1) + " enemigos";

        long deadAllies = data.allPlayers().stream()
                .filter(p -> myTeam.equals(p.team()) && p.isDead()).count();
        String deadAlliesText = deadAllies > 0 ? "Tenés " + deadAllies + " aliado(s) muerto(s) en este momento."
                : "Todos tus aliados están vivos.";

        String assistersList = "";
        if (deathEvent.Assisters() != null && !deathEvent.Assisters().isEmpty()) {
            assistersList = "Asistentes del killer: " + String.join(", ", deathEvent.Assisters());
        }

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

        String prompt = promptBuilder.buildDeathPrompt(data, deathEvent, req.myRole(), deathZone,
                visionText, fightType, killerComparison, assistersList, myTeam, deadAlliesText);

        if (req.screenshotBase64() != null && !req.screenshotBase64().isBlank()) {
            return runChatWithImageAnalysis(AnalysisTrigger.DEATH, prompt, req.screenshotBase64());
        }
        return runChatAnalysis(AnalysisTrigger.DEATH, prompt);
    }

    public AnalysisResponse analyzeObjectiveSpawn(ObjectiveSpawnAnalysisRequest req) {
        if (!openAIClient.isConfigured()) {
            return AnalysisResponse.error(AnalysisTrigger.OBJECTIVE_SPAWN, "OpenAI no configurado en el backend.");
        }

        LiveClientAllDataDTO data = req.data();

        int blueKills = data.allPlayers().stream()
                .filter(p -> "ORDER".equals(p.team()))
                .mapToInt(p -> p.scores() != null ? p.scores().kills() : 0).sum();
        int redKills = data.allPlayers().stream()
                .filter(p -> "CHAOS".equals(p.team()))
                .mapToInt(p -> p.scores() != null ? p.scores().kills() : 0).sum();

        LiveClientPlayerDTO myPlayer = findMyPlayer(data);
        String myTeam = myPlayer != null ? myPlayer.team() : "ORDER";
        boolean isBlue = "ORDER".equals(myTeam);

        int myKills = isBlue ? blueKills : redKills;
        int enemyKills = isBlue ? redKills : blueKills;
        int advantage = myKills - enemyKills;

        long deadAllies = data.allPlayers().stream()
                .filter(p -> myTeam.equals(p.team()) && p.isDead()).count();

        String prompt = promptBuilder.buildObjectiveSpawnPrompt(data, req.objective(), req.spawnTime(),
                req.currentGameTime(), myKills, enemyKills, advantage, deadAllies);

        return runChatAnalysis(AnalysisTrigger.OBJECTIVE_SPAWN, prompt);
    }

    public AnalysisResponse analyzeGameEnd(GameEndAnalysisRequest req) {
        if (!openAIClient.isConfigured()) {
            return AnalysisResponse.error(AnalysisTrigger.GAME_END, "OpenAI no configurado en el backend.");
        }

        AnalysisContext ctx = AnalysisContext.gameEnd(req.data(), req.myChampion(), req.mySummonerName());
        String prompt = promptBuilder.buildGameEndPrompt(ctx);
        return runChatAnalysis(AnalysisTrigger.GAME_END, prompt);
    }

    // ---- helpers ----

    private AnalysisResponse runChatAnalysis(AnalysisTrigger trigger, String prompt) {
        String response = openAIClient.chat(prompt);
        return finishWithOptionalTts(trigger, response);
    }

    private AnalysisResponse runChatWithImageAnalysis(AnalysisTrigger trigger, String prompt, String screenshotBase64) {
        String response = openAIClient.chatWithImage(prompt, screenshotBase64);
        return finishWithOptionalTts(trigger, response);
    }

    private AnalysisResponse finishWithOptionalTts(AnalysisTrigger trigger, String response) {
        if (response == null || response.startsWith("[Error]")) {
            return AnalysisResponse.error(trigger, response != null ? response : "Respuesta vacía de OpenAI");
        }

        if (ttsClient.isConfigured()) {
            byte[] audio = ttsClient.synthesize(response, "Speak in a friendly and encouraging tone, like a coach giving advice.");
            if (audio != null) {
                return AnalysisResponse.successWithAudio(trigger, response, Base64.getEncoder().encodeToString(audio));
            }
        }
        return AnalysisResponse.success(trigger, response);
    }

    private LiveClientPlayerDTO findMyPlayer(LiveClientAllDataDTO data) {
        if (data == null || data.activePlayer() == null || data.allPlayers() == null) return null;
        String myName = data.activePlayer().summonerName();
        String myBaseName = myName.contains("#") ? myName.substring(0, myName.indexOf("#")) : myName;

        for (LiveClientPlayerDTO p : data.allPlayers()) {
            if (p.summonerName() != null && (p.summonerName().equals(myName) || p.summonerName().equals(myBaseName))) {
                return p;
            }
            if (p.riotId() != null && (p.riotId().equals(myName) || p.riotId().contains(myBaseName))) {
                return p;
            }
        }
        for (LiveClientPlayerDTO p : data.allPlayers()) {
            if (p.championName() != null && p.championName().equals(data.activePlayer().championName())) {
                return p;
            }
        }
        return null;
    }

    private int countRecentWards(com.cuevas.powerspike.dto.LiveClientEventsDTO events, double deathTime) {
        if (events == null || events.Events() == null) return 0;
        int count = 0;
        List<LiveClientEventDTO> list = events.Events();
        for (LiveClientEventDTO e : list) {
            if (e.EventTime() > deathTime - 120 && e.EventTime() <= deathTime
                    && "WARD_PLACED".equals(e.EventName())) {
                count++;
            }
        }
        return count;
    }
}
