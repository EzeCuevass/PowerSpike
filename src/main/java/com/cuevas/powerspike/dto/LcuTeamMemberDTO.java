package com.cuevas.powerspike.dto;

public record LcuTeamMemberDTO(
        int cellId,
        int championId,
        int championPickIntent,
        String gameName,
        String tagLine,
        String puuid,
        long selectedSkinId,
        long spell1Id,
        long spell2Id,
        long summonerId,
        int team,
        long wardSkinId,
        String assignedPosition
) {}
