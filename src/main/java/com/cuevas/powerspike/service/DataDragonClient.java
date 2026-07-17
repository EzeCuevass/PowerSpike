package com.cuevas.powerspike.service;

import com.cuevas.powerspike.dto.ChampionData;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@Service
public class DataDragonClient {

    private final Map<Long, String> championMap = new HashMap<>();
    private final RestTemplate restTemplate;
    private String currentVersion = "14.10.1";

    public DataDragonClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void loadChampions() {
        try {
            String[] versions = restTemplate.getForObject(
                    "https://ddragon.leagueoflegends.com/api/versions.json", String[].class);
            if (versions != null && versions.length > 0) {
                currentVersion = versions[0];
            }
        } catch (Exception e) {
            System.out.println(">>> [DataDragon] No se pudo obtener versión, usando " + currentVersion);
        }

        System.out.println(">>> [DataDragon] Cargando campeones versión " + currentVersion);
        String url = "https://ddragon.leagueoflegends.com/cdn/" + currentVersion + "/data/en_US/champion.json";
        ChampionData response = restTemplate.getForObject(url, ChampionData.class);

        if (response != null && response.data() != null) {
            response.data().forEach((name, info) -> {
                championMap.put(Long.parseLong(info.key()), info.name());
            });
        }
    }

    public String getChampionName(long championId) {
        return championMap.get(championId);
    }

    public Map<Long, String> getAllChampions() {
        return championMap;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }
}
