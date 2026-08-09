package com.cuevas.powerspike.dto;

import java.util.List;

public record MatchDTO(String matchId, Info info) {
    public record Info(
        long gameDuration,
        String gameMode,
        long gameCreation,
        List<MatchParticipantDTO> participants
    ) {}
}