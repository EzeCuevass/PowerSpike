package com.cuevas.powerspike.ui;

import com.cuevas.powerspike.dto.CurrentGameInfo;
import com.cuevas.powerspike.dto.CurrentGameParticipant;
import com.cuevas.powerspike.dto.SummonerDTO;
import com.cuevas.powerspike.service.DataDragonClient;
import com.cuevas.powerspike.service.RiotApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unused")
public class MainController {

    @FXML private TextField gameNameField;
    @FXML private TextField tagLineField;
    @FXML private Button searchButton;
    @FXML private Button liveGameButton;
    @FXML private VBox summonerCard;
    @FXML private Label summonerNameLabel;
    @FXML private Label summonerLevelLabel;
    @FXML private Label summonerPuuidLabel;
    @FXML private ImageView profileIcon;
    @FXML private Label liveGameErrorLabel;
    @FXML private HBox teamsContainer;
    @FXML private VBox blueTeamPanel;
    @FXML private VBox redTeamPanel;

    private final RiotApiClient riotApiClient;
    private final DataDragonClient dataDragonClient;
    private String currentPuuid;

    public MainController(RiotApiClient riotApiClient, DataDragonClient dataDragonClient) {
        this.riotApiClient = riotApiClient;
        this.dataDragonClient = dataDragonClient;
    }

    @FXML
    public void initialize() {
        System.out.println(">>> MainController.initialize() called");
        System.out.println(">>> searchButton = " + searchButton);
        System.out.println(">>> gameNameField = " + gameNameField);

        if (searchButton != null) {
            searchButton.setOnAction(e -> buscarInvocador());
        }
        if (liveGameButton != null) {
            liveGameButton.setOnAction(e -> cargarLiveGame());
        }
        if (gameNameField != null) {
            gameNameField.setOnAction(e -> buscarInvocador());
        }
        if (tagLineField != null) {
            tagLineField.setOnAction(e -> buscarInvocador());
        }

        liveGameErrorLabel.setText("Listo para buscar");
    }

    private void buscarInvocador() {
        String gameName = gameNameField.getText().trim();
        String tagLine = tagLineField.getText().trim();
        if (gameName.isEmpty()) return;

        try {
            SummonerDTO summoner = riotApiClient.getSummonerByRiotId(gameName, tagLine);
            currentPuuid = summoner.puuid();
            mostrarInvocador(summoner);
            liveGameButton.setVisible(true);
            liveGameButton.setManaged(true);
            teamsContainer.setVisible(false);
            teamsContainer.setManaged(false);
            liveGameErrorLabel.setText("");
        } catch (Exception e) {
            summonerCard.setVisible(false);
            summonerCard.setManaged(false);
            liveGameButton.setVisible(false);
            liveGameButton.setManaged(false);
            liveGameErrorLabel.setText("Error: " + e.getMessage());
        }
    }

    private void mostrarInvocador(SummonerDTO summoner) {
        String iconUrl = "https://ddragon.leagueoflegends.com/cdn/14.10.1/img/profileicon/" + summoner.profileIconId() + ".png";
        profileIcon.setImage(new Image(iconUrl, true));

        summonerNameLabel.setText(summoner.gameName() + "#" + summoner.tagLine());
        summonerLevelLabel.setText("Nivel " + summoner.summonerLevel());
        summonerPuuidLabel.setText("PUUID: " + summoner.puuid());

        summonerCard.setVisible(true);
        summonerCard.setManaged(true);
    }

    private void cargarLiveGame() {
        if (currentPuuid == null) return;
        liveGameErrorLabel.setText("");

        try {
            CurrentGameInfo game = riotApiClient.getLiveGame(currentPuuid);

            if (game == null) {
                liveGameErrorLabel.setText("No estás en partida");
                return;
            }

            blueTeamPanel.getChildren().clear();
            redTeamPanel.getChildren().clear();

            Label blueTitle = new Label("EQUIPO AZUL");
            blueTitle.getStyleClass().add("section-title");
            blueTeamPanel.getChildren().add(blueTitle);

            Label redTitle = new Label("EQUIPO ROJO");
            redTitle.getStyleClass().add("section-title");
            redTeamPanel.getChildren().add(redTitle);

            long gameMinutes = game.gameLength() / 60;
            long gameSeconds = game.gameLength() % 60;
            String duration = String.format("%02d:%02d", gameMinutes, gameSeconds);

            Label gameModeLabel = new Label(game.gameMode() + " · " + duration);
            gameModeLabel.getStyleClass().add("info-label");

            for (CurrentGameParticipant p : game.participants()) {
                VBox card = newParticipantCard(p);
                if (p.teamId() == 100) {
                    blueTeamPanel.getChildren().add(card);
                } else {
                    redTeamPanel.getChildren().add(card);
                }
            }

            teamsContainer.setVisible(true);
            teamsContainer.setManaged(true);

        } catch (Exception e) {
            liveGameErrorLabel.setText("Error al cargar partida: " + e.getMessage());
        }
    }

    private VBox newParticipantCard(CurrentGameParticipant p) {
        VBox card = new VBox(6);
        card.getStyleClass().add("participant-card");

        String championName = dataDragonClient.getChampionName(p.championId());
        if (championName == null) championName = "Champion " + p.championId();

        String riotId = p.riotId();
        if (riotId == null || riotId.isEmpty()) riotId = "Unknown";

        HBox topRow = new HBox(10);

        String champIconUrl = "https://ddragon.leagueoflegends.com/cdn/14.10.1/img/champion/" + championName + ".png";
        ImageView champIcon = new ImageView();
        champIcon.setFitWidth(32);
        champIcon.setFitHeight(32);
        champIcon.getStyleClass().add("champion-icon");
        champIcon.setImage(new Image(champIconUrl, true));

        VBox nameBox = new VBox(2);
        Label nameLabel = new Label(riotId);
        nameLabel.getStyleClass().add("participant-name");
        Label champLabel = new Label(championName);
        champLabel.getStyleClass().add("participant-champion");

        nameBox.getChildren().addAll(nameLabel, champLabel);
        topRow.getChildren().addAll(champIcon, nameBox);
        card.getChildren().add(topRow);

        return card;
    }
}
