package com.cuevas.powerspike.service.analysis;

import com.cuevas.powerspike.analysis.*;
import com.cuevas.powerspike.service.AuthService;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cliente HTTP hacia los 5 endpoints de análisis de IA del backend
 * (/api/analysis/*). Reemplaza la llamada directa a OpenAIClient que antes
 * hacía AnalysisEngine en el mismo proceso.
 *
 * Publica el resultado en la misma ObjectProperty<AnalysisResult> que ya
 * consumían MainController y OverlayController, para no romper el contrato
 * de UI existente. Añade el token JWT de AuthService a cada request.
 */
@Service
public class AnalysisApiClient {

    private static final Logger log = LoggerFactory.getLogger(AnalysisApiClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final AuthService authService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ObjectProperty<AnalysisResult> latestResult = new SimpleObjectProperty<>();

    public AnalysisApiClient(RestTemplate restTemplate,
                             @Value("${backend.base-url:http://localhost:8080}") String baseUrl,
                             AuthService authService) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.authService = authService;
    }

    public void analyzeChampSelect(ChampSelectAnalysisRequest request) {
        submit(AnalysisTrigger.CHAMP_SELECT_END, "/api/analysis/champ-select", request);
    }

    public void analyzeMatchup(LiveClientMatchupAnalysisRequest request) {
        submit(AnalysisTrigger.LIVE_CLIENT_MATCHUP, "/api/analysis/matchup", request);
    }

    public void analyzeDeath(DeathAnalysisRequest request) {
        submit(AnalysisTrigger.DEATH, "/api/analysis/death", request);
    }

    public void analyzeObjectiveSpawn(ObjectiveSpawnAnalysisRequest request) {
        submit(AnalysisTrigger.OBJECTIVE_SPAWN, "/api/analysis/objective-spawn", request);
    }

    public void analyzeGameEnd(GameEndAnalysisRequest request) {
        submit(AnalysisTrigger.GAME_END, "/api/analysis/game-end", request);
    }

    private void submit(AnalysisTrigger trigger, String path, Object body) {
        executor.submit(() -> {
            AnalysisResult result;
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                String token = authService.getToken();
                if (token != null && !token.isBlank()) {
                    headers.set("Authorization", "Bearer " + token);
                }
                HttpEntity<Object> entity = new HttpEntity<>(body, headers);

                AnalysisResponse response = restTemplate.postForObject(baseUrl + path, entity, AnalysisResponse.class);
                if (response == null) {
                    result = AnalysisResult.error(trigger, null, "Respuesta vacía del backend.");
                } else if (response.success()) {
                    result = AnalysisResult.success(trigger, null, response.response());
                    if (response.audioBase64() != null) {
                        AudioPlayer.playBase64Mp3(response.audioBase64());
                    }
                } else {
                    result = AnalysisResult.error(trigger, null, response.errorMessage());
                }
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    // Token vencido/inválido → volver al login
                    authService.handleUnauthorized();
                    result = AnalysisResult.error(trigger, null, "Sesión expirada. Volvé a iniciar sesión.");
                } else {
                    result = AnalysisResult.error(trigger, null, "Backend: " + e.getStatusCode());
                }
            } catch (Exception e) {
                log.warn("No se pudo contactar al backend para {}: {}", path, e.getMessage());
                result = AnalysisResult.error(trigger, null, "Backend no disponible: " + e.getMessage());
            }
            AnalysisResult finalResult = result;
            Platform.runLater(() -> latestResult.set(finalResult));
        });
    }

    public ObjectProperty<AnalysisResult> latestResultProperty() { return latestResult; }
    public AnalysisResult getLatestResult() { return latestResult.get(); }
}
