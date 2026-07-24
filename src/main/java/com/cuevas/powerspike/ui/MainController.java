package com.cuevas.powerspike.ui;

import com.cuevas.powerspike.dto.*;
import com.cuevas.powerspike.service.DataDragonClient;
import com.cuevas.powerspike.service.GameStateService;
import com.cuevas.powerspike.service.RiotApiClient;
import com.cuevas.powerspike.service.analysis.AnalysisEngine;
import com.cuevas.powerspike.service.analysis.AnalysisResult;
import com.cuevas.powerspike.service.analysis.AnalysisTrigger;
import com.cuevas.powerspike.service.analysis.OpenAIClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

@Component
@SuppressWarnings("unused")
public class MainController {

    @FXML private TextField gameNameField;
    @FXML private TextField tagLineField;
    @FXML private Button searchButton;
    @FXML private HBox summonerHeaderCard;
    @FXML private Label summonerNameLabel;
    @FXML private Label summonerLevelLabel;
    @FXML private Label summonerPuuidLabel;
    @FXML private ImageView profileIcon;
    @FXML private Label searchErrorLabel;
    @FXML private Label statusDot;
    @FXML private Label statusLabel;
    @FXML private Label phaseLabel;
    @FXML private TabPane mainTabPane;
    @FXML private Tab profileTab;
    @FXML private Tab champSelectTab;
    @FXML private Tab liveGameTab;
    @FXML private HBox champSelectHeader;
    @FXML private Label csTimerLabel;
    @FXML private HBox csTeamsContainer;
    @FXML private VBox csBlueTeamPanel;
    @FXML private VBox csRedTeamPanel;
    @FXML private VBox csBansContainer;
    @FXML private VBox csBlueBans;
    @FXML private VBox csRedBans;
    @FXML private VBox csWaitingContainer;
    @FXML private VBox livePlayerCard;
    @FXML private Label liveChampLabel;
    @FXML private Label liveLevelLabel;
    @FXML private Label liveGoldLabel;
    @FXML private Label liveKillsLabel;
    @FXML private Label liveDeathsLabel;
    @FXML private Label liveAssistsLabel;
    @FXML private Label liveCsLabel;
    @FXML private HBox liveTeamsContainer;
    @FXML private VBox liveBlueTeamPanel;
    @FXML private VBox liveRedTeamPanel;
    @FXML private VBox liveWaitingContainer;
    @FXML private Tab coachTab;
    @FXML private VBox coachResultCard;
    @FXML private Label coachTriggerLabel;
    @FXML private Label coachTimeLabel;
    @FXML private Label coachResponseLabel;
    @FXML private VBox coachHistoryContainer;
    @FXML private VBox coachWaitingContainer;
    @FXML private Label coachConfigLabel;

    private final RiotApiClient riotApiClient;
    private final DataDragonClient dataDragonClient;
    private final GameStateService gameStateService;
    private final AnalysisEngine analysisEngine;
    private final OpenAIClient openAIClient;

    public MainController(RiotApiClient riotApiClient,
                          DataDragonClient dataDragonClient,
                          GameStateService gameStateService,
                          AnalysisEngine analysisEngine,
                          OpenAIClient openAIClient) {
        this.riotApiClient = riotApiClient;
        this.dataDragonClient = dataDragonClient;
        this.gameStateService = gameStateService;
        this.analysisEngine = analysisEngine;
        this.openAIClient = openAIClient;
    }

    @FXML
    public void initialize() {
        searchButton.setOnAction(e -> buscarInvocador());
        gameNameField.setOnAction(e -> buscarInvocador());
        tagLineField.setOnAction(e -> buscarInvocador());

        gameStateService.gamePhaseProperty().addListener((obs, oldVal, newVal) -> updatePhaseUI(newVal));
        gameStateService.champSelectProperty().addListener((obs, oldVal, newVal) -> updateChampSelectUI(newVal));
        gameStateService.liveGameDataProperty().addListener((obs, oldVal, newVal) -> updateLiveGameUI(newVal));
        analysisEngine.latestResultProperty().addListener((obs, oldVal, newVal) -> updateCoachUI(newVal));

        updatePhaseUI(gameStateService.getGamePhase());

        coachConfigLabel.setText(openAIClient.isConfigured()
                ? "IA configurada (gpt-5.4-mini)"
                : "IA no configurada - agregá openai.api.key en application.properties");
    }

    private void updatePhaseUI(String phase) {
        if (phase == null) phase = "CLOSED";

        switch (phase) {
            case "CLOSED" -> {
                statusDot.getStyleClass().setAll("status-dot", "status-dot-offline");
                statusLabel.setText("Desconectado");
                phaseLabel.setText("Esperando cliente de League...");
            }
            case "None" -> {
                statusDot.getStyleClass().setAll("status-dot", "status-dot-online");
                statusLabel.setText("Conectado");
                phaseLabel.setText("En el cliente");
            }
            case "Lobby" -> {
                statusDot.getStyleClass().setAll("status-dot", "status-dot-online");
                statusLabel.setText("Conectado");
                phaseLabel.setText("En lobby");
            }
            case "ChampSelect" -> {
                statusDot.getStyleClass().setAll("status-dot", "status-dot-active");
                statusLabel.setText("Champ Select");
                phaseLabel.setText("Seleccionando campeones...");
                mainTabPane.getSelectionModel().select(champSelectTab);
            }
            case "InProgress" -> {
                statusDot.getStyleClass().setAll("status-dot", "status-dot-ingame");
                statusLabel.setText("En partida");
                phaseLabel.setText("Partida en curso");
                mainTabPane.getSelectionModel().select(liveGameTab);
            }
            case "EndOfGame" -> {
                statusDot.getStyleClass().setAll("status-dot", "status-dot-online");
                statusLabel.setText("Conectado");
                phaseLabel.setText("Fin de la partida");
            }
            default -> {
                statusDot.getStyleClass().setAll("status-dot", "status-dot-online");
                statusLabel.setText("Conectado");
                phaseLabel.setText("Fase: " + phase);
            }
        }
    }

    private void updateChampSelectUI(LcuChampSelectDTO cs) {
        if (cs == null) {
            champSelectHeader.setVisible(false);
            champSelectHeader.setManaged(false);
            csTeamsContainer.setVisible(false);
            csTeamsContainer.setManaged(false);
            csBansContainer.setVisible(false);
            csBansContainer.setManaged(false);
            csWaitingContainer.setVisible(true);
            csWaitingContainer.setManaged(true);
            return;
        }

        csWaitingContainer.setVisible(false);
        csWaitingContainer.setManaged(false);
        champSelectHeader.setVisible(true);
        champSelectHeader.setManaged(true);
        csTeamsContainer.setVisible(true);
        csTeamsContainer.setManaged(true);

        if (cs.timer() != null) {
            long secs = cs.timer().adjustedTimeLeftInPhase() / 1000;
            csTimerLabel.setText(cs.timer().phase() + " - " + secs + "s");
        } else {
            csTimerLabel.setText("--");
        }

        csBlueTeamPanel.getChildren().retainAll(csBlueTeamPanel.getChildren().getFirst());
        csRedTeamPanel.getChildren().retainAll(csRedTeamPanel.getChildren().getFirst());

        for (LcuTeamMemberDTO m : cs.myTeam()) {
            csBlueTeamPanel.getChildren().add(createChampSelectCard(m));
        }
        for (LcuTeamMemberDTO m : cs.theirTeam()) {
            csRedTeamPanel.getChildren().add(createChampSelectCard(m));
        }

        if (cs.bans() != null) {
            csBansContainer.setVisible(true);
            csBansContainer.setManaged(true);

            csBlueBans.getChildren().clear();
            csRedBans.getChildren().clear();

            for (LcuBanDTO b : cs.bans().myTeamBans()) {
                csBlueBans.getChildren().add(createBanLabel(b));
            }
            for (LcuBanDTO b : cs.bans().theirTeamBans()) {
                csRedBans.getChildren().add(createBanLabel(b));
            }
        }
    }

    private VBox createChampSelectCard(LcuTeamMemberDTO m) {
        VBox card = new VBox(6);
        card.getStyleClass().add("participant-card");

        int champId = m.championId() > 0 ? m.championId() : m.championPickIntent();
        String championName = champId > 0 ? dataDragonClient.getChampionName(champId) : null;
        if (championName == null) championName = "Sin pick";
        String riotId = (m.gameName() != null && !m.gameName().isEmpty())
                ? m.gameName() + "#" + m.tagLine()
                : "Unknown";

        HBox topRow = new HBox(10);

        String champIconUrl = champId > 0
                ? "https://ddragon.leagueoflegends.com/cdn/" + dataDragonClient.getCurrentVersion() + "/img/champion/" + championName.replace(" ", "") + ".png"
                : null;
        ImageView champIcon = new ImageView();
        champIcon.setFitWidth(36);
        champIcon.setFitHeight(36);
        champIcon.getStyleClass().add("champion-icon");
        if (champIconUrl != null) {
            champIcon.setImage(new Image(champIconUrl, true));
        }

        VBox nameBox = new VBox(2);
        Label nameLabel = new Label(riotId);
        nameLabel.getStyleClass().add("participant-name");
        Label champLabel = new Label(championName);
        champLabel.getStyleClass().add(m.championId() > 0 ? "participant-champion" : "participant-hover");

        if (m.assignedPosition() != null && !m.assignedPosition().isEmpty()) {
            Label posLabel = new Label(m.assignedPosition());
            posLabel.getStyleClass().add("participant-position");
            nameBox.getChildren().addAll(nameLabel, champLabel, posLabel);
        } else {
            nameBox.getChildren().addAll(nameLabel, champLabel);
        }

        topRow.getChildren().addAll(champIcon, nameBox);
        card.getChildren().add(topRow);

        return card;
    }

    private Label createBanLabel(LcuBanDTO b) {
        String name = dataDragonClient.getChampionName(b.championId());
        if (name == null) name = "Champion " + b.championId();
        Label label = new Label("✕ " + name);
        label.getStyleClass().add("ban-label");
        return label;
    }

    private void updateLiveGameUI(LiveClientAllDataDTO data) {
        if (data == null || data.activePlayer() == null) {
            livePlayerCard.setVisible(false);
            livePlayerCard.setManaged(false);
            liveTeamsContainer.setVisible(false);
            liveTeamsContainer.setManaged(false);
            liveWaitingContainer.setVisible(true);
            liveWaitingContainer.setManaged(true);
            return;
        }

        liveWaitingContainer.setVisible(false);
        liveWaitingContainer.setManaged(false);
        livePlayerCard.setVisible(true);
        livePlayerCard.setManaged(true);
        liveTeamsContainer.setVisible(true);
        liveTeamsContainer.setManaged(true);

        LiveClientActivePlayerDTO ap = data.activePlayer();
        liveChampLabel.setText(ap.championName());
        liveLevelLabel.setText("Nivel " + ap.level());
        liveGoldLabel.setText(String.format("%.0fg", ap.currentGold()));

        LiveClientPlayerDTO myPlayer = data.allPlayers().stream()
                .filter(p -> p.summonerName().equals(ap.summonerName()) || p.riotId().equals(ap.summonerName()))
                .findFirst().orElse(null);

        if (myPlayer != null && myPlayer.scores() != null) {
            liveKillsLabel.setText(String.valueOf(myPlayer.scores().kills()));
            liveDeathsLabel.setText(String.valueOf(myPlayer.scores().deaths()));
            liveAssistsLabel.setText(String.valueOf(myPlayer.scores().assists()));
            liveCsLabel.setText(String.valueOf(myPlayer.scores().creepScore()));
        }

        liveBlueTeamPanel.getChildren().retainAll(liveBlueTeamPanel.getChildren().getFirst());
        liveRedTeamPanel.getChildren().retainAll(liveRedTeamPanel.getChildren().getFirst());

        for (LiveClientPlayerDTO p : data.allPlayers()) {
            VBox card = createLivePlayerCard(p);
            if ("ORDER".equals(p.team())) {
                liveBlueTeamPanel.getChildren().add(card);
            } else {
                liveRedTeamPanel.getChildren().add(card);
            }
        }
    }

    private VBox createLivePlayerCard(LiveClientPlayerDTO p) {
        VBox card = new VBox(4);
        card.getStyleClass().add("participant-card");

        String displayName = (p.riotId() != null && !p.riotId().isEmpty()) ? p.riotId() : p.summonerName();

        HBox topRow = new HBox(8);

        VBox infoBox = new VBox(2);
        Label nameLabel = new Label(displayName);
        nameLabel.getStyleClass().add("participant-name");
        Label champLabel = new Label(p.championName() + " - Lv." + p.level());
        champLabel.getStyleClass().add("participant-champion");
        infoBox.getChildren().addAll(nameLabel, champLabel);

        VBox statsBox = new VBox(2);
        statsBox.getStyleClass().add("live-stats-box");
        if (p.scores() != null) {
            Label kdaLabel = new Label(p.scores().kills() + "/" + p.scores().deaths() + "/" + p.scores().assists());
            kdaLabel.getStyleClass().add("live-kda");
            Label csLabel = new Label(p.scores().creepScore() + " CS");
            csLabel.getStyleClass().add("live-cs");
            statsBox.getChildren().addAll(kdaLabel, csLabel);
        }

        topRow.getChildren().addAll(infoBox, statsBox);
        card.getChildren().add(topRow);

        return card;
    }

    private void buscarInvocador() {
        String gameName = gameNameField.getText().trim();
        String tagLine = tagLineField.getText().trim();
        if (gameName.isEmpty()) return;

        searchErrorLabel.setText("");

        try {
            SummonerDTO summoner = riotApiClient.getSummonerByRiotId(gameName, tagLine);
            gameStateService.setMyPuuid(summoner.puuid());
            gameStateService.setMyRiotId(summoner.gameName(), summoner.tagLine());
            mostrarInvocador(summoner);
        } catch (Exception e) {
            summonerHeaderCard.setVisible(false);
            summonerHeaderCard.setManaged(false);
            searchErrorLabel.setText("Error: " + e.getMessage());
        }
    }

    private void mostrarInvocador(SummonerDTO summoner) {
        String iconUrl = "https://ddragon.leagueoflegends.com/cdn/" + dataDragonClient.getCurrentVersion() + "/img/profileicon/" + summoner.profileIconId() + ".png";
        profileIcon.setImage(new Image(iconUrl, true));

        summonerNameLabel.setText(summoner.gameName() + "#" + summoner.tagLine());
        summonerLevelLabel.setText("Nivel " + summoner.summonerLevel());

        summonerHeaderCard.setVisible(true);
        summonerHeaderCard.setManaged(true);
    }

    private void updateCoachUI(AnalysisResult result) {
        if (result == null) return;

        coachResultCard.setVisible(true);
        coachResultCard.setManaged(true);
        coachWaitingContainer.setVisible(false);
        coachWaitingContainer.setManaged(false);

        String triggerName = switch (result.trigger()) {
            case CHAMP_SELECT_END -> "CHAMP SELECT";
            case LIVE_CLIENT_MATCHUP -> "MATCHUP CONCRETO";
            case DEATH -> "MUERTE";
            case GAME_END -> "POST-GAME";
        };
        coachTriggerLabel.setText(triggerName);
        coachTimeLabel.setText(new SimpleDateFormat("HH:mm:ss").format(new Date(result.timestamp())));

        if (result.success()) {
            coachResponseLabel.setText(result.response());
            coachResponseLabel.getStyleClass().setAll("coach-response");
        } else {
            coachResponseLabel.setText("Error: " + result.errorMessage());
            coachResponseLabel.getStyleClass().setAll("coach-response", "error-text");
        }

        VBox historyCard = new VBox(8);
        historyCard.getStyleClass().add("card");
        historyCard.getStyleClass().add("coach-history-card");

        Label historyTrigger = new Label(triggerName + " - " + coachTimeLabel.getText());
        historyTrigger.getStyleClass().add("coach-trigger-label");
        Label historyResponse = new Label(result.success() ? result.response() : "Error: " + result.errorMessage());
        historyResponse.getStyleClass().add("coach-response");
        historyResponse.setWrapText(true);

        historyCard.getChildren().addAll(historyTrigger, historyResponse);
        coachHistoryContainer.getChildren().addFirst(historyCard);

        mainTabPane.getSelectionModel().select(coachTab);
    }
}
