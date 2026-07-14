package com.cuevas.powerspike.service;

import com.cuevas.powerspike.dto.AccountDTO;
import com.cuevas.powerspike.dto.CurrentGameInfo;
import com.cuevas.powerspike.dto.SummonerDTO;
import com.cuevas.powerspike.exception.GameNotActiveException;
import com.cuevas.powerspike.exception.SummonerNotFoundException;
import com.cuevas.powerspike.model.SummonerEntity;
import com.cuevas.powerspike.repository.SummonerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class RiotApiClient {
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String accountUrl;
    private final String platformUrl;
    private final SummonerRepository summonerRepository;

    public RiotApiClient(RestTemplate restTemplate,
                         @Value("${riot.api.key}") String apiKey,
                         @Value("${riot.account-url}") String accountUrl,
                         @Value("${riot.platform-url}") String platformUrl,
                         SummonerRepository summonerRepository) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.accountUrl = accountUrl;
        this.platformUrl = platformUrl;
        this.summonerRepository = summonerRepository;
    }
    // Recibe por parametros el gamename y tagline
    // Devuelve un AccountDTO con la información del invocador
    public AccountDTO getAccountByRiotId(String gameName, String tagLine) {
        String url = accountUrl + "/riot/account/v1/accounts/by-riot-id/" + gameName + "/" + tagLine;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        // Peticion GET a la API de Riot para obtener la información del invocador
        ResponseEntity<AccountDTO> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                AccountDTO.class
        );

        return response.getBody();
    }
    // Recibe por parametros el puuid del invocador
    // Devuelve un SummonerDTO con la información del invocador
    public SummonerDTO getSummonerByPuuid(String puuid) {
        String url = platformUrl + "/lol/summoner/v4/summoners/by-puuid/" + puuid;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        // Peticion GET a la API de Riot para obtener la información del invocador
        ResponseEntity<SummonerDTO> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                SummonerDTO.class
        );

        return response.getBody();
    }

    public SummonerDTO getSummonerByRiotId(String gameName, String tagLine) {
        Optional<SummonerEntity> cached = summonerRepository.findByGameNameAndTagLine(gameName, tagLine);
        // Si el invocador está en caché y la información es reciente, se devuelve la información de la caché
        if (cached.isPresent()) {
            SummonerEntity entity = cached.get();
            if (entity.getLastUpdated().isAfter(LocalDateTime.now().minusMinutes(5))) {
                return new SummonerDTO(
                        entity.getGameName(),
                        entity.getTagLine(),
                        entity.getPuuid(),
                        entity.getSummonerLevel(),
                        entity.getProfileIconId(),
                        entity.getRevisionDate()
                );
            }
        }

        // Si no está en caché o la información está desactualizada, se obtiene de la API de Riot
        AccountDTO account = getAccountByRiotId(gameName, tagLine);
        SummonerDTO summoner = getSummonerByPuuid(account.puuid());

        SummonerEntity entity = new SummonerEntity(
                account.puuid(), account.gameName(), account.tagLine(),
                summoner.summonerLevel(), summoner.profileIconId(), summoner.revisionDate()
        );
        summonerRepository.save(entity);

        return new SummonerDTO(
                account.gameName(), account.tagLine(),
                account.puuid(),
                summoner.summonerLevel(), summoner.profileIconId(), summoner.revisionDate()
        );
    }
    // Recibe por parametros el puuid del invocador
    // Devuelve un CurrentGameInfo con la información del juego en vivo
    public CurrentGameInfo getLiveGame(String puuid) {
        String url = platformUrl + "/lol/spectator/v5/active-games/by-summoner/" + puuid;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<CurrentGameInfo> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    CurrentGameInfo.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new GameNotActiveException("El invocador no está en partida");
            }
            throw e;
        }
    }
}
