package com.cuevas.powerspike.dto;

public record LiveClientActivePlayerDTO(
        String summonerName,
        int level,
        double currentGold,
        String championName,
        LiveClientChampionStatsDTO championStats,
        LiveClientAbilitiesDTO abilities
) {}
