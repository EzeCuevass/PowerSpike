package com.cuevas.powerspike.service;

import com.cuevas.powerspike.dto.ChampionData;
import com.cuevas.powerspike.dto.ItemData;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@Service
public class DataDragonClient {

    private static final Logger log = LoggerFactory.getLogger(DataDragonClient.class);

    private final Map<Long, String> championMap = new HashMap<>();
    private final Map<Integer, String> itemMap = new HashMap<>();
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
            log.warn("No se pudo obtener versión, usando {}", currentVersion);
        }

        log.info("Cargando campeones versión {}", currentVersion);
        String url = "https://ddragon.leagueoflegends.com/cdn/" + currentVersion + "/data/en_US/champion.json";
        ChampionData response = restTemplate.getForObject(url, ChampionData.class);

        if (response != null && response.data() != null) {
            response.data().forEach((name, info) -> {
                championMap.put(Long.parseLong(info.key()), info.name());
            });
        }

        loadItems();
    }

    private void loadItems() {
        try {
            String url = "https://ddragon.leagueoflegends.com/cdn/" + currentVersion + "/data/en_US/item.json";
            ItemData response = restTemplate.getForObject(url, ItemData.class);
            if (response != null && response.data() != null) {
                response.data().forEach((id, info) -> {
                    itemMap.put(Integer.parseInt(id), info.name());
                });
            }
            log.info("Cargados {} items", itemMap.size());
        } catch (Exception e) {
            log.warn("No se pudieron cargar items: {}", e.getMessage());
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

    public String getItemName(int itemId) {
        return itemMap.getOrDefault(itemId, "Item " + itemId);
    }
}
