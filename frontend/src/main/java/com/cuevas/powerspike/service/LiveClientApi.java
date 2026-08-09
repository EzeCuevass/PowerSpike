package com.cuevas.powerspike.service;

import com.cuevas.powerspike.dto.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;

@Service
public class LiveClientApi {

    private final RestTemplate liveClientRestTemplate;

    public LiveClientApi(@Qualifier("liveClientRestTemplate") RestTemplate liveClientRestTemplate) {
        this.liveClientRestTemplate = liveClientRestTemplate;
    }

    public LiveClientAllDataDTO getAllGameData() {
        String url = "https://127.0.0.1:2999/liveclientdata/allgamedata";
        return liveClientRestTemplate.getForObject(url, LiveClientAllDataDTO.class);
    }

    public List<LiveClientEventDTO> getEvents() {
        String url = "https://127.0.0.1:2999/liveclientdata/eventdata";
        LiveClientEventsDTO events = liveClientRestTemplate.getForObject(url, LiveClientEventsDTO.class);
        return events != null ? events.Events() : List.of();
    }

    public LiveClientChampionStatsDTO getChampionStats() {
        String url = "https://127.0.0.1:2999/liveclientdata/activeplayer";
        LiveClientActivePlayerDTO activePlayer = liveClientRestTemplate.getForObject(url, LiveClientActivePlayerDTO.class);
        return activePlayer != null ? activePlayer.championStats() : null;
    }

    public LiveClientSummaryDTO getSummary() {
        LiveClientAllDataDTO allData = getAllGameData();
        if (allData == null || allData.activePlayer() == null) {
            return new LiveClientSummaryDTO(false, null, null, 0, 0, null, null, null, List.of());
        }

        LiveClientActivePlayerDTO ap = allData.activePlayer();
        LiveClientPlayerDTO myPlayer = allData.allPlayers().stream()
                .filter(p -> p.summonerName().equals(ap.summonerName()))
                .findFirst()
                .orElse(null);

        LiveClientScoresDTO scores = myPlayer != null ? myPlayer.scores() : null;
        List<LiveClientEventDTO> events = allData.events() != null
                ? allData.events().Events()
                : List.of();

        return new LiveClientSummaryDTO(
                true,
                ap.summonerName(),
                ap.championName(),
                ap.level(),
                ap.currentGold(),
                scores,
                ap.championStats(),
                ap.abilities(),
                events
        );
    }
}
