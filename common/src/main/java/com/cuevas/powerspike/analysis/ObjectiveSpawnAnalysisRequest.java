package com.cuevas.powerspike.analysis;

import com.cuevas.powerspike.dto.LiveClientAllDataDTO;

/**
 * Request de aviso de objetivo próximo a spawnear (Dragón/Heraldo/Barón/Larvas).
 * El backend calcula ventaja de kills y aliados muertos a partir de `data`.
 */
public record ObjectiveSpawnAnalysisRequest(
        LiveClientAllDataDTO data,
        String objective,
        double spawnTime,
        double currentGameTime
) {}
