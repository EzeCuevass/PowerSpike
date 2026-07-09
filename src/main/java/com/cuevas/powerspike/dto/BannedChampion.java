package com.cuevas.powerspike.dto;

public record BannedChampion(
        int pickTurn,
        long championId,
        long teamId
) {}
