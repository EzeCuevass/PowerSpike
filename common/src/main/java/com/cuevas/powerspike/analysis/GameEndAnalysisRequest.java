package com.cuevas.powerspike.analysis;

import com.cuevas.powerspike.dto.LiveClientAllDataDTO;

/**
 * Request de análisis post-partida (resumen, aciertos, errores, áreas de mejora).
 */
public record GameEndAnalysisRequest(
        LiveClientAllDataDTO data,
        String myChampion,
        String mySummonerName
) {}
