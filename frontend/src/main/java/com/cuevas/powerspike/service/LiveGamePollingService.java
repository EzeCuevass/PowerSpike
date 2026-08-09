package com.cuevas.powerspike.service;

import com.cuevas.powerspike.dto.LiveClientAllDataDTO;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LiveGamePollingService {

    private final LiveClientApi liveClientApi;
    private final GameStateService gameStateService;
    private boolean lastPollSuccessful = false;

    public LiveGamePollingService(LiveClientApi liveClientApi, GameStateService gameStateService) {
        this.liveClientApi = liveClientApi;
        this.gameStateService = gameStateService;
    }

    @Scheduled(fixedDelay = 2000)
    public void pollLiveGameData() {
        try {
            LiveClientAllDataDTO data = liveClientApi.getAllGameData();
            if (data != null && data.activePlayer() != null) {
                gameStateService.updateLiveGameData(data);
                lastPollSuccessful = true;
            } else {
                if (lastPollSuccessful) {
                    gameStateService.clearLiveGameData();
                }
                lastPollSuccessful = false;
            }
        } catch (Exception e) {
            if (lastPollSuccessful) {
                gameStateService.clearLiveGameData();
            }
            lastPollSuccessful = false;
        }
    }
}
