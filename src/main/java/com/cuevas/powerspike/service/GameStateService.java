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

    public String getGamePhase() { return gamePhase.get(); }
    public LcuChampSelectDTO getChampSelect() { return champSelect.get(); }
    public LiveClientAllDataDTO getLiveGameData() { return liveGameData.get(); }
    public boolean isInGame() { return inGame.get(); }
    public boolean isInChampSelect() { return inChampSelect.get(); }
}
