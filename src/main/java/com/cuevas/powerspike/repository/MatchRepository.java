package com.cuevas.powerspike.repository;

import com.cuevas.powerspike.model.MatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<MatchEntity, String> {
    List<MatchEntity> findByPuuidOrderByGameCreationDesc(String puuid);

    @Query("SELECT m.championId, m.championName, COUNT(m) as games, " +
           "SUM(CASE WHEN m.win=true THEN 1 ELSE 0 END) as wins " +
           "FROM MatchEntity m WHERE m.puuid = :puuid " +
           "GROUP BY m.championId, m.championName " +
           "ORDER BY games DESC")
    List<Object[]> findWinratesByPuuid(@Param("puuid") String puuid);
}