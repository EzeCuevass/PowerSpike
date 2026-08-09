package com.cuevas.powerspike.dto;

import java.util.List;

public record CurrentGameParticipant(
        String puuid,
        long championId,
        long teamId,
        long spell1Id,
        long spell2Id,
        long profileIconId,
        long lastSelectedSkinIndex,
        boolean bot,
        String riotId,
        Perks perks,
        List<GameCustomizationObject> gameCustomizationObjects
) {}
