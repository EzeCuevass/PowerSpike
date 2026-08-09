package com.cuevas.powerspike.dto;

public record SummonerDTO(
        String gameName,
        String tagLine,
        String puuid,
        Long summonerLevel,
        Integer profileIconId,
        Long revisionDate
) {}
