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

    public DataDragonClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void loadChampions() {
        String url = "https://ddragon.leagueoflegends.com/cdn/14.10.1/data/en_US/champion.json";
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
}
