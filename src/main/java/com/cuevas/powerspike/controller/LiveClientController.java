package com.cuevas.powerspike.controller;

import com.cuevas.powerspike.dto.*;
import com.cuevas.powerspike.service.LiveClientApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/live-client")
public class LiveClientController {

    private final LiveClientApi liveClientApi;

    public LiveClientController(LiveClientApi liveClientApi) {
        this.liveClientApi = liveClientApi;
    }

    @GetMapping("/all")
    public LiveClientAllDataDTO getAll() {
        return liveClientApi.getAllGameData();
    }

    @GetMapping("/events")
    public List<LiveClientEventDTO> getEvents() {
        return liveClientApi.getEvents();
    }

    @GetMapping("/stats")
    public LiveClientChampionStatsDTO getStats() {
        return liveClientApi.getChampionStats();
    }

    @GetMapping("/summary")
    public LiveClientSummaryDTO getSummary() {
        return liveClientApi.getSummary();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        LiveClientAllDataDTO data = liveClientApi.getAllGameData();
        if (data == null || data.activePlayer() == null) {
            return Map.of("inGame", false, "message", "No estás en partida o el juego no está abierto");
        }
        return Map.of(
            "inGame", true,
            "summoner", data.activePlayer().summonerName(),
            "champion", data.activePlayer().championName(),
            "level", data.activePlayer().level()
        );
    }
}
