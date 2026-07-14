package com.cuevas.powerspike.dto;

public record LiveClientScoresDTO(
        int kills,
        int deaths,
        int assists,
        int creepScore,
        double wardScore
) {}
