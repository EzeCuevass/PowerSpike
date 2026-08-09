package com.cuevas.powerspike.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class MatchEntity {

    @Id
    private String matchId;
    private String puuid;
    private int championId;
    private String championName;
    private int kills;
    private int deaths;
    private int assists;
    private boolean win;
    private long gameDuration;
    private long gameCreation;
    private String gameMode;
    private String lane;
    private int cs;
    private long damageDealt;
    private double visionScore;
    private String items;
    private String enemyChampions;
    private LocalDateTime fetchedAt;

    public MatchEntity() {}

    public MatchEntity(String matchId, String puuid, int championId, String championName,
                       int kills, int deaths, int assists, boolean win, long gameDuration,
                       long gameCreation, String gameMode, String lane, int cs,
                       long damageDealt, double visionScore, String items, String enemyChampions) {
        this.matchId = matchId;
        this.puuid = puuid;
        this.championId = championId;
        this.championName = championName;
        this.kills = kills;
        this.deaths = deaths;
        this.assists = assists;
        this.win = win;
        this.gameDuration = gameDuration;
        this.gameCreation = gameCreation;
        this.gameMode = gameMode;
        this.lane = lane;
        this.cs = cs;
        this.damageDealt = damageDealt;
        this.visionScore = visionScore;
        this.items = items;
        this.enemyChampions = enemyChampions;
        this.fetchedAt = LocalDateTime.now();
    }

    public String getMatchId() { return matchId; }
    public String getPuuid() { return puuid; }
    public int getChampionId() { return championId; }
    public String getChampionName() { return championName; }
    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
    public int getAssists() { return assists; }
    public boolean isWin() { return win; }
    public long getGameDuration() { return gameDuration; }
    public long getGameCreation() { return gameCreation; }
    public String getGameMode() { return gameMode; }
    public String getLane() { return lane; }
    public int getCs() { return cs; }
    public long getDamageDealt() { return damageDealt; }
    public double getVisionScore() { return visionScore; }
    public String getItems() { return items; }
    public String getEnemyChampions() { return enemyChampions; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}