package com.cuevas.powerspike.dto;

import java.util.List;

public record CurrentGameInfo(
        long gameId,
        String gameType,
        long gameStartTime,
        long mapId,
        long gameLength,
        String platformId,
        String gameMode,
        long gameQueueConfigId,
        List<BannedChampion> bannedChampions,
        List<CurrentGameParticipant> participants,
        Observer observers
) {}
