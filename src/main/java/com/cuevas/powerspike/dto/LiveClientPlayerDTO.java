package com.cuevas.powerspike.dto;

import java.util.List;

public record LiveClientPlayerDTO(
        String championName,
        int level,
        String position,
        String riotId,
        String summonerName,
        String team,
        boolean isDead,
        LiveClientScoresDTO scores,
        List<LiveClientItemDTO> items,
        LiveClientSummonerSpellsDTO summonerSpells,
        LiveClientRunesDTO runes
) {}
