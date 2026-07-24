package com.cuevas.powerspike.service;

import com.cuevas.powerspike.dto.LcuChampSelectDTO;
import com.cuevas.powerspike.dto.LiveClientAllDataDTO;
import javafx.application.Platform;
import javafx.beans.property.*;
import org.springframework.stereotype.Service;

@Service
public class GameStateService {

    private final StringProperty gamePhase = new SimpleStringProperty("CLOSED");
    private final ObjectProperty<LcuChampSelectDTO> champSelect = new SimpleObjectProperty<>();
    private final ObjectProperty<LiveClientAllDataDTO> liveGameData = new SimpleObjectProperty<>();
    private final BooleanProperty inGame = new SimpleBooleanProperty(false);
    private final BooleanProperty inChampSelect = new SimpleBooleanProperty(false);
    private final StringProperty activePlayerName = new SimpleStringProperty("");
    private final StringProperty myPuuid = new SimpleStringProperty("");
    private final StringProperty myRole = new SimpleStringProperty("");
    private final StringProperty myGameName = new SimpleStringProperty("");
    private final StringProperty myTagLine = new SimpleStringProperty("");
    private final StringProperty myEnemyChampion = new SimpleStringProperty("");
    private final StringProperty myEnemyName = new SimpleStringProperty("");

    public void setMyPuuid(String puuid) {
        Platform.runLater(() -> myPuuid.set(puuid != null ? puuid : ""));
    }

    public void setMyRole(String role) {
        Platform.runLater(() -> myRole.set(role != null ? role : ""));
    }

    public void setMyRiotId(String gameName, String tagLine) {
        Platform.runLater(() -> {
            myGameName.set(gameName != null ? gameName : "");
            myTagLine.set(tagLine != null ? tagLine : "");
        });
    }

    public void setMyEnemyChampion(String champ) {
        Platform.runLater(() -> myEnemyChampion.set(champ != null ? champ : ""));
    }

    public void setMyEnemyName(String name) {
        Platform.runLater(() -> myEnemyName.set(name != null ? name : ""));
    }

    public void updatePhase(String phase) {
        Platform.runLater(() -> {
            gamePhase.set(phase);
            inChampSelect.set("ChampSelect".equals(phase));
            inGame.set("InProgress".equals(phase));
        });
    }

    public void updateChampSelect(LcuChampSelectDTO data) {
        Platform.runLater(() -> champSelect.set(data));
    }

    public void clearChampSelect() {
        Platform.runLater(() -> champSelect.set(null));
    }

    public void updateLiveGameData(LiveClientAllDataDTO data) {
        Platform.runLater(() -> {
            liveGameData.set(data);
            if (data != null && data.activePlayer() != null) {
                activePlayerName.set(data.activePlayer().summonerName());
                inGame.set(true);
            }
        });
    }

    public void clearLiveGameData() {
        Platform.runLater(() -> {
            liveGameData.set(null);
            activePlayerName.set("");
            inGame.set(false);
        });
    }

    public StringProperty gamePhaseProperty() { return gamePhase; }
    public ObjectProperty<LcuChampSelectDTO> champSelectProperty() { return champSelect; }
    public ObjectProperty<LiveClientAllDataDTO> liveGameDataProperty() { return liveGameData; }
    public BooleanProperty inGameProperty() { return inGame; }
    public BooleanProperty inChampSelectProperty() { return inChampSelect; }
    public StringProperty activePlayerNameProperty() { return activePlayerName; }
    public StringProperty myPuuidProperty() { return myPuuid; }
    public StringProperty myRoleProperty() { return myRole; }
    public String getMyPuuid() { return myPuuid.get(); }
    public String getMyRole() { return myRole.get(); }
    public String getMyGameName() { return myGameName.get(); }
    public String getMyTagLine() { return myTagLine.get(); }
    public String getActivePlayerName() { return activePlayerName.get(); }
    public String getMyEnemyChampion() { return myEnemyChampion.get(); }
    public String getMyEnemyName() { return myEnemyName.get(); }

    public String getGamePhase() { return gamePhase.get(); }
    public LcuChampSelectDTO getChampSelect() { return champSelect.get(); }
    public LiveClientAllDataDTO getLiveGameData() { return liveGameData.get(); }
    public boolean isInGame() { return inGame.get(); }
    public boolean isInChampSelect() { return inChampSelect.get(); }
}
