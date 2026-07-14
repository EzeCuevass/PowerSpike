package com.cuevas.powerspike.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "summoners")
public class SummonerEntity {

    @Id
    private String puuid;
    private String gameName;
    private String tagLine;
    private Long summonerLevel;
    private Integer profileIconId;
    private Long revisionDate;
    private LocalDateTime lastUpdated;

    public SummonerEntity() {}
    // Constructor
    public SummonerEntity(String puuid, String gameName, String tagLine,
                          Long summonerLevel, Integer profileIconId, Long revisionDate) {
        this.puuid = puuid;
        this.gameName = gameName;
        this.tagLine = tagLine;
        this.summonerLevel = summonerLevel;
        this.profileIconId = profileIconId;
        this.revisionDate = revisionDate;
        this.lastUpdated = LocalDateTime.now();
    }
    // Getters & Setters
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
    public Long getSummonerLevel() {
        return summonerLevel;
    }
    public void setSummonerLevel(Long summonerLevel) {
        this.summonerLevel = summonerLevel;
    }
    public Integer getProfileIconId() {
        return profileIconId;
    }
    public void setProfileIconId(Integer profileIconId) {
        this.profileIconId = profileIconId;
    }
    public Long getRevisionDate() {
        return revisionDate;
    }
    public void setRevisionDate(Long revisionDate) {
        this.revisionDate = revisionDate;
    }
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
