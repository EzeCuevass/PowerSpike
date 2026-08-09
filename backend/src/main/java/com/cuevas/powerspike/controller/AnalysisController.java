package com.cuevas.powerspike.controller;

import com.cuevas.powerspike.analysis.*;
import com.cuevas.powerspike.service.analysis.AnalysisService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone el motor de análisis de IA al frontend. El frontend detecta cuándo
 * disparar cada análisis (ya tiene los datos del juego en tiempo real) y
 * llama a uno de estos 5 endpoints con el contexto necesario.
 */
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/champ-select")
    public AnalysisResponse champSelect(@RequestBody ChampSelectAnalysisRequest request) {
        return analysisService.analyzeChampSelect(request);
    }

    @PostMapping("/matchup")
    public AnalysisResponse matchup(@RequestBody LiveClientMatchupAnalysisRequest request) {
        return analysisService.analyzeLiveClientMatchup(request);
    }

    @PostMapping("/death")
    public AnalysisResponse death(@RequestBody DeathAnalysisRequest request) {
        return analysisService.analyzeDeath(request);
    }

    @PostMapping("/objective-spawn")
    public AnalysisResponse objectiveSpawn(@RequestBody ObjectiveSpawnAnalysisRequest request) {
        return analysisService.analyzeObjectiveSpawn(request);
    }

    @PostMapping("/game-end")
    public AnalysisResponse gameEnd(@RequestBody GameEndAnalysisRequest request) {
        return analysisService.analyzeGameEnd(request);
    }
}
