package com.cuevas.powerspike.service;

import com.cuevas.powerspike.dto.ChampionWinrateDTO;
import com.cuevas.powerspike.dto.MatchSummaryDTO;
import com.cuevas.powerspike.model.MatchEntity;
import com.cuevas.powerspike.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    private final RiotApiClient riotApiClient;
    private final MatchRepository matchRepository;
    private final DataDragonClient dataDragonClient;

    public MatchService(RiotApiClient riotApiClient,
                        MatchRepository matchRepository,
                        DataDragonClient dataDragonClient) {
        this.riotApiClient = riotApiClient;
        this.matchRepository = matchRepository;
        this.dataDragonClient = dataDragonClient;
    }

    @SuppressWarnings("unchecked")
    public List<MatchSummaryDTO> getMatchHistory(String gameName, String tagLine, int count) {
        try {
            var account = riotApiClient.getAccountByRiotId(gameName, tagLine);
            String puuid = account.puuid();

            // Primero buscar en caché
            List<MatchEntity> cached = matchRepository.findByPuuidOrderByGameCreationDesc(puuid);

            // Si no hay suficientes en caché, consultar API
            if (cached.size() < count) {
                List<String> matchIds = riotApiClient.getMatchIds(puuid, count);
                for (String matchId : matchIds) {
                    if (cached.stream().noneMatch(m -> m.getMatchId().equals(matchId))) {
                        try {
                            Map<String, Object> detail = riotApiClient.getMatchDetail(matchId);
                            MatchEntity entity = extractPlayerStats(matchId, puuid, detail);
                            if (entity != null) {
                                matchRepository.save(entity);
                                cached.add(entity);
                            }
                        } catch (Exception e) {
                            log.warn("No se pudo obtener match {}: {}", matchId, e.getMessage());
                        }
                    }
                }
            }

            // Convertir a DTOs y ordenar
            return cached.stream()
                    .sorted((a, b) -> Long.compare(b.getGameCreation(), a.getGameCreation()))
                    .limit(count)
                    .map(this::toSummaryDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error obteniendo match history: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private MatchEntity extractPlayerStats(String matchId, String puuid, Map<String, Object> detail) {
        try {
            Map<String, Object> info = (Map<String, Object>) detail.get("info");
            if (info == null) return null;

            long gameDuration = ((Number) info.get("gameDuration")).longValue();
            String gameMode = (String) info.get("gameMode");
            long gameCreation = ((Number) info.get("gameCreation")).longValue();

            List<Map<String, Object>> participants = (List<Map<String, Object>>) info.get("participants");
            if (participants == null) return null;

            for (Map<String, Object> p : participants) {
                if (puuid.equals(p.get("puuid"))) {
                    int championId = ((Number) p.get("championId")).intValue();
                    String championName = (String) p.get("championName");
                    int kills = ((Number) p.get("kills")).intValue();
                    int deaths = ((Number) p.get("deaths")).intValue();
                    int assists = ((Number) p.get("assists")).intValue();
                    boolean win = (Boolean) p.get("win");
                    int cs = ((Number) p.get("totalMinionsKilled")).intValue();
                    long damage = ((Number) p.get("totalDamageDealtToChampions")).longValue();
                    double vision = ((Number) p.get("visionScore")).doubleValue();
                    String lane = (String) p.get("lane");
                    int item0 = getItemInt(p, "item0");
                    int item1 = getItemInt(p, "item1");
                    int item2 = getItemInt(p, "item2");
                    int item3 = getItemInt(p, "item3");
                    int item4 = getItemInt(p, "item4");
                    int item5 = getItemInt(p, "item5");
                    int item6 = getItemInt(p, "item6");

                    String items = List.of(item0, item1, item2, item3, item4, item5, item6).stream()
                            .filter(i -> i > 0)
                            .map(i -> String.valueOf(i))
                            .collect(Collectors.joining(","));

                    // Extraer IDs de campeones enemigos
                    List<String> enemyIds = participants.stream()
                            .filter(ep -> !puuid.equals(ep.get("puuid")))
                            .map(ep -> String.valueOf(((Number) ep.get("championId")).intValue()))
                            .distinct()
                            .collect(Collectors.toList());
                    String enemyChampions = String.join(",", enemyIds);

                    return new MatchEntity(matchId, puuid, championId, championName,
                            kills, deaths, assists, win, gameDuration, gameCreation,
                            gameMode, lane, cs, damage, vision, items, enemyChampions);
                }
            }
        } catch (Exception e) {
            log.debug("Error extrayendo stats de match {}: {}", matchId, e.getMessage());
        }
        return null;
    }

    private int getItemInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }

    private MatchSummaryDTO toSummaryDTO(MatchEntity e) {
        List<String> itemNames = new ArrayList<>();
        if (e.getItems() != null && !e.getItems().isEmpty()) {
            for (String idStr : e.getItems().split(",")) {
                try {
                    int id = Integer.parseInt(idStr.trim());
                    itemNames.add(dataDragonClient.getItemName(id));
                } catch (NumberFormatException ignored) {}
            }
        }

        return new MatchSummaryDTO(
                e.getMatchId(),
                e.getChampionId(),
                e.getChampionName() != null ? e.getChampionName() : "Desconocido",
                e.getKills(), e.getDeaths(), e.getAssists(),
                e.isWin(),
                e.getGameDuration(),
                e.getGameCreation(),
                e.getGameMode(),
                e.getLane(),
                e.getCs(),
                e.getDamageDealt(),
                e.getVisionScore(),
                itemNames
        );
    }

    public List<ChampionWinrateDTO> getChampionWinrates(String gameName, String tagLine, String puuid) {
        List<Object[]> rows = matchRepository.findWinratesByPuuid(puuid);
        return rows.stream().map(row -> {
            int championId = ((Number) row[0]).intValue();
            String championName = (String) row[1];
            long games = ((Number) row[2]).longValue();
            long wins = ((Number) row[3]).longValue();
            double wr = games > 0 ? (double) wins / games * 100 : 0;
            return new ChampionWinrateDTO(championId, championName, games, wins, Math.round(wr * 10) / 10.0);
        }).collect(Collectors.toList());
    }

    public List<ChampionWinrateDTO> getWorstEnemies(String gameName, String tagLine, String puuid) {
        List<MatchEntity> allMatches = matchRepository.findByPuuidOrderByGameCreationDesc(puuid);
        Map<Integer, long[]> enemyStats = new HashMap<>();

        for (MatchEntity m : allMatches) {
            if (m.getEnemyChampions() == null || m.getEnemyChampions().isEmpty()) continue;
            for (String idStr : m.getEnemyChampions().split(",")) {
                int enemyId = Integer.parseInt(idStr.trim());
                enemyStats.putIfAbsent(enemyId, new long[2]); // [games, wins]
                enemyStats.get(enemyId)[0]++;
                if (m.isWin()) enemyStats.get(enemyId)[1]++;
            }
        }

        return enemyStats.entrySet().stream()
                .map(e -> {
                    int champId = e.getKey();
                    long games = e.getValue()[0];
                    long wins = e.getValue()[1];
                    double wr = games > 0 ? (double) wins / games * 100 : 0;
                    String name = dataDragonClient.getChampionName(champId);
                    return new ChampionWinrateDTO(champId, name != null ? name : "Champion " + champId,
                            games, wins, Math.round(wr * 10) / 10.0);
                })
                .filter(c -> c.games() >= 2)
                .sorted((a, b) -> Double.compare(a.winrate(), b.winrate()))
                .limit(5)
                .collect(Collectors.toList());
    }
}
