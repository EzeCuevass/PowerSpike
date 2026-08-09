package com.cuevas.powerspike.analysis;

import com.cuevas.powerspike.dto.LiveClientAllDataDTO;
import com.cuevas.powerspike.dto.LiveClientEventDTO;

/**
 * Request de análisis de muerte. Incluye el estado completo de la partida,
 * el evento de la muerte y, opcionalmente, un screenshot en base64 (JPEG)
 * para el análisis multimodal (GPT Luna 5.6).
 */
public record DeathAnalysisRequest(
        LiveClientAllDataDTO data,
        LiveClientEventDTO deathEvent,
        String myRole,
        String screenshotBase64
) {}
