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
                if (!lastPollSuccessful) {
                    System.out.println(">>> [LivePoll] OK - Jugador: " + data.activePlayer().summonerName() + " - " + data.activePlayer().championName());
                }
                gameStateService.updateLiveGameData(data);
                lastPollSuccessful = true;
            } else {
                if (lastPollSuccessful) {
                    gameStateService.clearLiveGameData();
                }
                lastPollSuccessful = false;
            }
        } catch (Exception e) {
            Throwable cause = e.getCause();
            while (cause != null && cause.getCause() != null) cause = cause.getCause();
            String detail = cause != null ? cause.getClass().getSimpleName() + ": " + cause.getMessage() : e.getMessage();
            System.out.println(">>> [LivePoll] Error: " + e.getClass().getSimpleName() + " -> " + detail);
            if (lastPollSuccessful) {
                gameStateService.clearLiveGameData();
            }
            lastPollSuccessful = false;
        }
    }
}
