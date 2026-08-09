package com.cuevas.powerspike.dto;

public record LcuTimerDTO(
        long adjustedTimeLeftInPhase,
        long internalNowInEpochMs,
        boolean isInfinite,
        String phase,
        long totalTimeInPhase
) {}
