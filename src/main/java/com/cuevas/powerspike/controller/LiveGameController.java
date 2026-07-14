package com.cuevas.powerspike.controller;

import com.cuevas.powerspike.dto.CurrentGameInfo;
import com.cuevas.powerspike.dto.SummonerDTO;
import com.cuevas.powerspike.service.RiotApiClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LiveGameController {

    private final RiotApiClient riotApiClient;

    public LiveGameController(RiotApiClient riotApiClient) {
        this.riotApiClient = riotApiClient;
    }

    @GetMapping("/live-game/{gameName}/{tagLine}")
    public CurrentGameInfo getLiveGame(@PathVariable String gameName,
                                        @PathVariable String tagLine) {
        // Agarra el summoner usando el RiotApiClient y luego usar su puuid para obtener la información del juego en vivo
        SummonerDTO summoner = riotApiClient.getSummonerByRiotId(gameName, tagLine);
        return riotApiClient.getLiveGame(summoner.puuid());
    }
}
