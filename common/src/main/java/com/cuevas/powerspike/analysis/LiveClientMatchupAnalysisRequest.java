package com.cuevas.powerspike.analysis;

import com.cuevas.powerspike.dto.LiveClientAllDataDTO;

/**
 * Request de análisis de matchup concreto, disparado cuando el Live Client
 * conecta por primera vez (roles reales de los 10 jugadores).
 */
public record LiveClientMatchupAnalysisRequest(
        LiveClientAllDataDTO data
) {}
