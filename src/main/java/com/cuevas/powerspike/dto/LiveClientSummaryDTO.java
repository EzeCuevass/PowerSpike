package com.cuevas.powerspike.dto;

import java.util.List;

public record LiveClientSummaryDTO(
        boolean inGame,
        String summonerName,
        String championName,
        int level,
        double currentGold,
        LiveClientScoresDTO scores,
        LiveClientChampionStatsDTO championStats,
        LiveClientAbilitiesDTO abilities,
        List<LiveClientEventDTO> recentEvents
) {}
