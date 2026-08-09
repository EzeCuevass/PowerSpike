package com.cuevas.powerspike.dto;

public record ChampionWinrateDTO(
    int championId,
    String championName,
    long games,
    long wins,
    double winrate
) {}