package com.cuevas.powerspike.service;

import com.cuevas.powerspike.dto.CurrentGameInfo;
import com.cuevas.powerspike.dto.MatchSummaryDTO;
import com.cuevas.powerspike.dto.SummonerDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente HTTP hacia el backend de PowerSpike (alojado en la nube o en
 * localhost en desarrollo). Reemplaza el acceso directo a RiotApiClient,
 * DataDragonClient y MatchService que antes estaban en el mismo proceso.
 *
 * El frontend nunca ve la API key de Riot: todo pasa por estos endpoints.
 */
@Service
public class BackendApiClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    // Pequeño caché en memoria de nombres de campeón, para no pegarle al backend
    // en cada render de una card de champ select / live game.
    private final Map<Long, String> championNameCache = new ConcurrentHashMap<>();
    private volatile String cachedVersion;

    public BackendApiClient(RestTemplate restTemplate,
                            @Value("${backend.base-url:http://localhost:8080}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public String getChampionName(long championId) {
        if (championId <= 0) return null;
        return championNameCache.computeIfAbsent(championId, id -> {
            try {
                return restTemplate.getForObject(baseUrl + "/api/champions/{id}", String.class, id);
            } catch (Exception e) {
                return null;
            }
        });
    }

    public String getCurrentVersion() {
        if (cachedVersion != null) return cachedVersion;
        try {
            cachedVersion = restTemplate.getForObject(baseUrl + "/api/champions/version", String.class);
        } catch (Exception e) {
            cachedVersion = "14.10.1"; // fallback si el backend no responde
        }
        return cachedVersion;
    }

    public SummonerDTO getSummoner(String gameName, String tagLine) {
        return restTemplate.getForObject(baseUrl + "/api/summoner/{gameName}/{tagLine}",
                SummonerDTO.class, gameName, tagLine);
    }

    public List<MatchSummaryDTO> getMatchHistory(String gameName, String tagLine, int count) {
        MatchSummaryDTO[] matches = restTemplate.getForObject(
                baseUrl + "/api/matches/{gameName}/{tagLine}?count={count}",
                MatchSummaryDTO[].class, gameName, tagLine, count);
        return matches != null ? List.of(matches) : List.of();
    }

    public CurrentGameInfo getLiveGameBySpectator(String gameName, String tagLine) {
        return restTemplate.getForObject(baseUrl + "/api/live-game/{gameName}/{tagLine}",
                CurrentGameInfo.class, gameName, tagLine);
    }

    public boolean isReachable() {
        try {
            restTemplate.getForObject(baseUrl + "/api/champions/version", String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
