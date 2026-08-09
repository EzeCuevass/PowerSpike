package com.cuevas.powerspike.service.analysis;

import com.cuevas.powerspike.analysis.AnalysisTrigger;

/**
 * Modelo de UI: resultado de un análisis, tal como lo consume MainController
 * y OverlayController. Es puramente local al frontend (no viaja por HTTP;
 * lo que sí viaja es AnalysisResponse, del módulo common).
 */
public record AnalysisResult(
    AnalysisTrigger trigger,
    String prompt,
    String response,
    long timestamp,
    boolean success,
    String errorMessage
) {
    public static AnalysisResult success(AnalysisTrigger trigger, String prompt, String response) {
        return new AnalysisResult(trigger, prompt, response, System.currentTimeMillis(), true, null);
    }

    public static AnalysisResult error(AnalysisTrigger trigger, String prompt, String errorMessage) {
        return new AnalysisResult(trigger, prompt, null, System.currentTimeMillis(), false, errorMessage);
    }
}
