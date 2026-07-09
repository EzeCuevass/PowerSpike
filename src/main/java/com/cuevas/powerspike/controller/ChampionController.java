package com.cuevas.powerspike.controller;

import com.cuevas.powerspike.service.DataDragonClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChampionController {

    private final DataDragonClient dataDragonClient;

    public ChampionController(DataDragonClient dataDragonClient) {
        this.dataDragonClient = dataDragonClient;
    }

    @GetMapping("/champions")
    public Map<Long, String> getAllChampions() {
        return dataDragonClient.getAllChampions();
    }

    @GetMapping("/champions/{id}")
    public String getChampionName(@PathVariable long id) {
        String name = dataDragonClient.getChampionName(id);
        return name != null ? name : "Unknown champion";
    }
}
