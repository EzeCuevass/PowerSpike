package com.cuevas.powerspike.analysis;

/**
 * Respuesta del backend a cualquiera de los 5 endpoints de análisis.
 * `audioBase64` viene informado solo si `openai.tts.enabled=true` en el backend.
 */
public record AnalysisResponse(
        AnalysisTrigger trigger,
        String response,
        boolean success,
        String errorMessage,
        String audioBase64,
        long timestamp
) {
    public static AnalysisResponse success(AnalysisTrigger trigger, String response) {
        return new AnalysisResponse(trigger, response, true, null, null, System.currentTimeMillis());
    }

    public static AnalysisResponse successWithAudio(AnalysisTrigger trigger, String response, String audioBase64) {
        return new AnalysisResponse(trigger, response, true, null, audioBase64, System.currentTimeMillis());
    }

    public static AnalysisResponse error(AnalysisTrigger trigger, String errorMessage) {
        return new AnalysisResponse(trigger, null, false, errorMessage, null, System.currentTimeMillis());
    }
}
