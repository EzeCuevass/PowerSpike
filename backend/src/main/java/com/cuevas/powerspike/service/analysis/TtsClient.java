package com.cuevas.powerspike.service.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Cliente para la API de Text-to-Speech de OpenAI (gpt-4o-mini-tts).
 *
 * A diferencia de la versión original (que reproducía el audio directamente con
 * JavaFX Media), esta versión del backend es headless: solo genera el audio y
 * devuelve los bytes (mp3). La reproducción queda a cargo del frontend, que
 * recibe el audio en base64 dentro de AnalysisResponse.
 *
 * Voces disponibles: alloy, ash, ballad, coral, echo, fable, nova, onyx, sage, shimmer, verse, marin, cedar
 */
@Service
public class TtsClient {

    private static final Logger log = LoggerFactory.getLogger(TtsClient.class);

    private static final String API_URL = "https://api.openai.com/v1/audio/speech";
    private static final String MODEL = "gpt-4o-mini-tts";
    private static final String VOICE = "coral"; // Voz recomendada para español

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final boolean ttsEnabled;

    public TtsClient(RestTemplate restTemplate,
                     @Value("${openai.api.key:}") String apiKey,
                     @Value("${openai.tts.enabled:false}") boolean ttsEnabled) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.ttsEnabled = ttsEnabled;
    }

    /**
     * Genera audio (mp3) a partir de texto. Devuelve null si TTS no está
     * habilitado/configurado o si falla la llamada a OpenAI.
     *
     * @param text El texto a convertir en audio
     * @param instructions Instrucciones de estilo para la voz (opcional)
     */
    public byte[] synthesize(String text, String instructions) {
        if (!isConfigured() || text == null || text.isBlank()) {
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> body = Map.of(
                "model", MODEL,
                "voice", VOICE,
                "input", text,
                "instructions", instructions != null ? instructions : "Speak in a clear and natural tone.",
                "response_format", "mp3"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            return restTemplate.postForObject(API_URL, request, byte[].class);
        } catch (Exception e) {
            log.error("TTS Error: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    public boolean isConfigured() {
        return ttsEnabled && apiKey != null && !apiKey.isBlank();
    }
}
