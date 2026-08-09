package com.cuevas.powerspike.dto;

import java.util.List;

public record MatchSummaryDTO(
    String matchId,
    int championId,
    String championName,
    int kills, int deaths, int assists,
    boolean win,
    long gameDuration,
    long gameCreation,
    String gameMode,
    String lane,
    int cs,
    long damageDealt,
    double visionScore,
    List<String> items
) {}