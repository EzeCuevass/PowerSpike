package com.cuevas.powerspike.service.analysis;

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
