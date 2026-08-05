package com.cuevas.powerspike.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
@Entity
public class ActiveSession {
    @Id
    private Long id = 1L;

    private String puuid;
    private String gameName;
    private String tagLine;
    private Long profileIconId;
    private Long summonerLevel;

    public ActiveSession() {
    }
    public ActiveSession(String puuid, String gameName, String tagLine, Long profileIconId, Long summonerLevel) {
        this.puuid = puuid;
        this.gameName = gameName;
        this.tagLine = tagLine;
        this.profileIconId = profileIconId;
        this.summonerLevel = summonerLevel;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPuuid() {
        return puuid;
    }

    public void setPuuid(String puuid) {
        this.puuid = puuid;
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public String getTagLine() {
        return tagLine;
    }

    public void setTagLine(String tagLine) {
        this.tagLine = tagLine;
    }

    public Long getProfileIconId() {
        return profileIconId;
    }

    public void setProfileIconId(Long profileIconId) {
        this.profileIconId = profileIconId;
    }

    public Long getSummonerLevel() {
        return summonerLevel;
    }

    public void setSummonerLevel(Long summonerLevel) {
        this.summonerLevel = summonerLevel;
    }
}
