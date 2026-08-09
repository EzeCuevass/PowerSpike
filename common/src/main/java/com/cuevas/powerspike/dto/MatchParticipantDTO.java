package com.cuevas.powerspike.dto;

public record MatchParticipantDTO(
    String puuid,
    int championId,
    String championName,
    int kills,
    int deaths,
    int assists,
    boolean win,
    int teamId,
    int totalMinionsKilled,
    long totalDamageDealtToChampions,
    int visionScore,
    long goldEarned,
    int item0, int item1, int item2, int item3, int item4, int item5, int item6,
    int summoner1Id, int summoner2Id,
    String lane, String role
) {}