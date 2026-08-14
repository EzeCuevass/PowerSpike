package com.cuevas.powerspike.ui;

import com.cuevas.powerspike.analysis.AnalysisTrigger;
import com.cuevas.powerspike.dto.*;
import com.cuevas.powerspike.service.AuthService;
import com.cuevas.powerspike.service.BackendApiClient;
import com.cuevas.powerspike.service.GameStateService;
import com.cuevas.powerspike.service.analysis.AnalysisApiClient;
import com.cuevas.powerspike.service.analysis.AnalysisResult;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
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

    @FXML private VBox loginPanel;
    @FXML private TextField loginEmailField;
    @FXML private Label loginEmailLabel;
    @FXML private Label loginUsernameLabel;
    @FXML private TextField loginUsernameField;
    @FXML private PasswordField loginPasswordField;
    @FXML private Label loginConfirmLabel;
    @FXML private PasswordField loginConfirmField;
    @FXML private Label loginErrorLabel;
    @FXML private Button loginSubmitButton;
    @FXML private Label loginSwitchPrompt;
    @FXML private Button loginSwitchButton;

    @FXML private TextField gameNameField;
    @FXML private TextField tagLineField;
    @FXML private Button searchButton;
    @FXML private HBox summonerHeaderCard;
    @FXML private Button logoutButton;
    @FXML private Label summonerNameLabel;
    @FXML private Label summonerLevelLabel;
    @FXML private Label summonerPuuidLabel;
    @FXML private ImageView profileIcon;
    @FXML private Label searchErrorLabel;
    @FXML private VBox matchHistorySection;
    @FXML private VBox matchHistoryContainer;
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

    private final BackendApiClient backendApiClient;
    private final GameStateService gameStateService;
    private final AnalysisApiClient analysisApiClient;
    private final AuthService authService;

    private boolean registerMode = false;

    public MainController(BackendApiClient backendApiClient,
                          GameStateService gameStateService,
                          AnalysisApiClient analysisApiClient,
                          AuthService authService) {
        this.backendApiClient = backendApiClient;
        this.gameStateService = gameStateService;
        this.analysisApiClient = analysisApiClient;
        this.authService = authService;
    }

    @FXML
    public void initialize() {
        searchButton.setOnAction(e -> buscarInvocador());
        gameNameField.setOnAction(e -> buscarInvocador());
        tagLineField.setOnAction(e -> buscarInvocador());

        gameStateService.gamePhaseProperty().addListener((obs, oldVal, newVal) -> updatePhaseUI(newVal));
        gameStateService.champSelectProperty().addListener((obs, oldVal, newVal) -> updateChampSelectUI(newVal));
        gameStateService.liveGameDataProperty().addListener((obs, oldVal, newVal) -> updateLiveGameUI(newVal));
        analysisApiClient.latestResultProperty().addListener((obs, oldVal, newVal) -> updateCoachUI(newVal));

        logoutButton.setOnAction(e -> cerrarSesion());

        setupLoginPanel();

        updatePhaseUI(gameStateService.getGamePhase());

        // Restaurar sesión de cuenta (auto-login) y de Riot si existen
        authService.loggedInProperty().addListener((obs, oldVal, newVal) -> updateLoginVisibility(newVal));
        authService.restoreSession();
        updateLoginVisibility(authService.isLoggedIn());

        if (gameStateService.hasActiveSession()) {
            restaurarSesionEnHeader();
        }

//        coachConfigLabel.setText("Coach IA (backend: " + (backendApiClient.isReachable() ? "conectado" : "no disponible") + ")");
    }

    private void setupLoginPanel() {
        loginSubmitButton.setOnAction(e -> submitAuth());
        loginSwitchButton.setOnAction(e -> toggleAuthMode());
        loginPasswordField.setOnAction(e -> submitAuth());
        loginConfirmField.setOnAction(e -> submitAuth());
    }

    private void toggleAuthMode() {
        registerMode = !registerMode;
        boolean reg = registerMode;
        loginUsernameLabel.setVisible(reg);
        loginUsernameLabel.setManaged(reg);
        loginUsernameField.setVisible(reg);
        loginUsernameField.setManaged(reg);
        loginConfirmLabel.setVisible(reg);
        loginConfirmLabel.setManaged(reg);
        loginConfirmField.setVisible(reg);
        loginConfirmField.setManaged(reg);
        loginSubmitButton.setText(reg ? "Crear cuenta" : "Iniciar sesión");
        loginSwitchPrompt.setText(reg ? "¿Ya tenés cuenta?" : "¿No tenés cuenta?");
        loginSwitchButton.setText(reg ? "Iniciar sesión" : "Registrate");
        loginErrorLabel.setText("");
    }

    private void submitAuth() {
        String mail = loginEmailField.getText().trim();
        String password = loginPasswordField.getText();
        if (mail.isEmpty() || password.isEmpty()) {
            loginErrorLabel.setText("Completá email y contraseña.");
            return;
        }

        loginErrorLabel.setText("Procesando...");
        loginSubmitButton.setDisable(true);

        new Thread(() -> {
            String error;
            if (registerMode) {
                String username = loginUsernameField.getText().trim();
                String confirm = loginConfirmField.getText();
                if (username.isEmpty()) {
                    javafx.application.Platform.runLater(() -> {
                        loginErrorLabel.setText("Completá el username.");
                        loginSubmitButton.setDisable(false);
                    });
                    return;
                }
                if (!password.equals(confirm)) {
                    javafx.application.Platform.runLater(() -> {
                        loginErrorLabel.setText("Las contraseñas no coinciden.");
                        loginSubmitButton.setDisable(false);
                    });
                    return;
                }
                error = authService.register(mail, username, password);
            } else {
                error = authService.login(mail, password);
            }

            javafx.application.Platform.runLater(() -> {
                if (error != null) {
                    loginErrorLabel.setText(error);
                }
                loginSubmitButton.setDisable(false);
            });
        }).start();
    }

    private void updateLoginVisibility(boolean loggedIn) {
        if (loginPanel == null || mainTabPane == null) return;
        loginPanel.setVisible(!loggedIn);
        loginPanel.setManaged(!loggedIn);
        mainTabPane.setVisible(loggedIn);
        mainTabPane.setManaged(loggedIn);
        if (loggedIn) {
            loginErrorLabel.setText("");
            loginPasswordField.clear();
            loginConfirmField.clear();
        }
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
        String championName = champId > 0 ? backendApiClient.getChampionName(champId) : null;
        if (championName == null) championName = "Sin pick";
        String riotId = (m.gameName() != null && !m.gameName().isEmpty())
                ? m.gameName() + "#" + m.tagLine()
                : "Unknown";

        HBox topRow = new HBox(10);

        String champIconUrl = champId > 0
                ? "https://ddragon.leagueoflegends.com/cdn/" + backendApiClient.getCurrentVersion() + "/img/champion/" + championName.replace(" ", "") + ".png"
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
        String name = backendApiClient.getChampionName(b.championId());
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

        // Limpiar datos anteriores antes de cargar los nuevos
        matchHistoryContainer.getChildren().clear();
        matchHistorySection.setVisible(false);
        matchHistorySection.setManaged(false);

        try {
            SummonerDTO summoner = backendApiClient.getSummoner(gameName, tagLine);
            gameStateService.setMyPuuid(summoner.puuid());
            gameStateService.setMyRiotId(summoner.gameName(), summoner.tagLine());
            gameStateService.saveSession(summoner.puuid(), summoner.gameName(), summoner.tagLine(),
                    summoner.profileIconId() != null ? summoner.profileIconId().longValue() : 0L,
                    summoner.summonerLevel() != null ? summoner.summonerLevel() : 0L);
            mostrarInvocador(summoner);
            cargarMatchHistory(gameName, tagLine);
        } catch (Exception e) {
            summonerHeaderCard.setVisible(false);
            summonerHeaderCard.setManaged(false);
            searchErrorLabel.setText("Error: " + e.getMessage());
        }
    }

    private void mostrarInvocador(SummonerDTO summoner) {
        String iconUrl = "https://ddragon.leagueoflegends.com/cdn/" + backendApiClient.getCurrentVersion() + "/img/profileicon/" + summoner.profileIconId() + ".png";
        profileIcon.setImage(new Image(iconUrl, true));

        summonerNameLabel.setText(summoner.gameName() + "#" + summoner.tagLine());
        summonerLevelLabel.setText("Nivel " + summoner.summonerLevel());

        summonerHeaderCard.setVisible(true);
        summonerHeaderCard.setManaged(true);
    }

    private void restaurarSesionEnHeader() {
        String gameName = gameStateService.getMyGameName();
        String tagLine = gameStateService.getMyTagLine();
        if (gameName.isEmpty()) return;

        summonerNameLabel.setText(gameName + "#" + tagLine);

        long iconId = gameStateService.getSavedProfileIconId();
        if (iconId > 0) {
            String iconUrl = "https://ddragon.leagueoflegends.com/cdn/" + backendApiClient.getCurrentVersion() + "/img/profileicon/" + iconId + ".png";
            profileIcon.setImage(new Image(iconUrl, true));
        }

        long level = gameStateService.getSavedSummonerLevel();
        summonerLevelLabel.setText(level > 0 ? "Nivel " + level : "Sesión restaurada");

        summonerHeaderCard.setVisible(true);
        summonerHeaderCard.setManaged(true);

        cargarMatchHistory(gameName, tagLine);
    }

    private void cerrarSesion() {
        gameStateService.clearSession();
        authService.logout();
        summonerHeaderCard.setVisible(false);
        summonerHeaderCard.setManaged(false);
        matchHistoryContainer.getChildren().clear();
        matchHistorySection.setVisible(false);
        matchHistorySection.setManaged(false);
        searchErrorLabel.setText("Sesión cerrada");
    }

    private void cargarMatchHistory(String gameName, String tagLine) {
        new Thread(() -> {
            try {
                var matches = backendApiClient.getMatchHistory(gameName, tagLine, 20);
                javafx.application.Platform.runLater(() -> mostrarMatchHistory(matches));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                    searchErrorLabel.setText("Error cargando historial: " + e.getMessage()));
            }
        }).start();
    }

    private void mostrarMatchHistory(java.util.List<MatchSummaryDTO> matches) {
        matchHistoryContainer.getChildren().clear();
        if (matches.isEmpty()) {
            matchHistorySection.setManaged(false);
            matchHistorySection.setVisible(false);
            return;
        }
        matchHistorySection.setVisible(true);
        matchHistorySection.setManaged(true);

        String version = backendApiClient.getCurrentVersion();

        for (MatchSummaryDTO m : matches) {
            HBox card = new HBox(12);
            card.getStyleClass().add("match-card");
            card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            // Icono del campeón
            ImageView champIcon = new ImageView();
            champIcon.setFitWidth(40);
            champIcon.setFitHeight(40);
            champIcon.getStyleClass().add("match-champion-icon");
            String champUrl = "https://ddragon.leagueoflegends.com/cdn/" + version + "/img/champion/"
                    + (m.championName() != null ? m.championName().replace(" ", "") : "Unknown") + ".png";
            champIcon.setImage(new Image(champUrl, true));

            Label champLabel = new Label(m.championName() != null ? m.championName() : "Desconocido");
            champLabel.getStyleClass().add(m.win() ? "match-champ-win" : "match-champ-loss");

            Label kdaLabel = new Label(m.kills() + "/" + m.deaths() + "/" + m.assists());
            kdaLabel.getStyleClass().add(m.win() ? "match-kda-win" : "match-kda-loss");

            long durationSecs = m.gameDuration();
            String duration = String.format("%d:%02d", durationSecs / 60, durationSecs % 60);

            Label resultLabel = new Label(m.win() ? "VICTORIA" : "DERROTA");
            resultLabel.getStyleClass().add(m.win() ? "match-win" : "match-loss");

            Label infoLabel = new Label(duration + " · " + getTimeAgo(m.gameCreation()));
            infoLabel.getStyleClass().add("match-info");

            VBox leftBox = new VBox(4);
            leftBox.getChildren().addAll(champLabel, kdaLabel, infoLabel);

            // Items + trinket (iconos)
            HBox itemsBox = new HBox(4);
            itemsBox.getStyleClass().add("match-items-row");
            if (m.items() != null) {
                for (Integer itemId : m.items()) {
                    if (itemId == null || itemId <= 0) continue;
                    ImageView itemIcon = new ImageView();
                    itemIcon.setFitWidth(24);
                    itemIcon.setFitHeight(24);
                    itemIcon.getStyleClass().add("match-item-icon");
                    String itemUrl = "https://ddragon.leagueoflegends.com/cdn/" + version + "/img/item/" + itemId + ".png";
                    itemIcon.setImage(new Image(itemUrl, true));
                    itemsBox.getChildren().add(itemIcon);
                }
            }

            VBox rightBox = new VBox(4);
            rightBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            rightBox.getChildren().addAll(resultLabel, itemsBox);
            HBox.setHgrow(rightBox, javafx.scene.layout.Priority.ALWAYS);

            card.getChildren().addAll(champIcon, leftBox, rightBox);
            matchHistoryContainer.getChildren().add(card);
        }
    }

    private String getTimeAgo(long gameCreationMs) {
        long diffMs = System.currentTimeMillis() - gameCreationMs;
        long mins = diffMs / 60000;
        if (mins < 60) return "hace " + mins + "m";
        long hours = mins / 60;
        if (hours < 24) return "hace " + hours + "h";
        long days = hours / 24;
        return "hace " + days + "d";
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
            case OBJECTIVE_SPAWN -> "OBJETIVO";
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
