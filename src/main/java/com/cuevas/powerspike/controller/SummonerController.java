package com.cuevas.powerspike.controller;

import com.cuevas.powerspike.dto.SummonerDTO;
import com.cuevas.powerspike.service.RiotApiClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SummonerController {
    private final RiotApiClient riotApiClient;

    public SummonerController(RiotApiClient riotApiClient){
        this.riotApiClient = riotApiClient;
    }

    @GetMapping("/summoner/{gameName}/{tagLine}")
    public SummonerDTO getSummoner(@PathVariable String gameName, @PathVariable String tagLine){
        return riotApiClient.getSummonerByRiotId(gameName, tagLine);
    }
}
