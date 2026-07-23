package com.cuevas.powerspike.service.analysis;

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
 * Convierte texto a audio y lo reproduce usando JavaFX Media.
 * Voces disponibles: alloy, ash, ballad, coral, echo, fable, nova, onyx, sage, shimmer, verse, marin, cedar
 */
@Service
public class TtsClient {

    private static final String API_URL = "https://api.openai.com/v1/audio/speech";
    private static final String MODEL = "gpt-4o-mini-tts";
    private static final String VOICE = "coral"; // Voz recomendada para español

    private final RestTemplate restTemplate;
    private final String apiKey;

    public TtsClient(RestTemplate restTemplate,
                     @Value("${openai.api.key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Genera audio a partir de texto y lo reproduce.
     *
     * @param text El texto a convertir en audio
     * @param instructions Instrucciones de estilo para la voz (opcional)
     */
    public void speak(String text, String instructions) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
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
            byte[] audioData = restTemplate.postForObject(API_URL, request, byte[].class);

            if (audioData != null) {
                playAudio(audioData);
            }
        } catch (Exception e) {
            System.out.println(">>> [TTS] Error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * Reproduce el audio usando JavaFX Media.
     * Guarda el audio en un archivo temporal y lo reproduce.
     */
    private void playAudio(byte[] audioData) {
        javafx.application.Platform.runLater(() -> {
            try {
                // Guardar audio en archivo temporal
                java.io.File tempFile = java.io.File.createTempFile("tts_", ".mp3");
                tempFile.deleteOnExit();
                java.nio.file.Files.write(tempFile.toPath(), audioData);

                // Reproducir con JavaFX Media
                String uri = tempFile.toURI().toString();
                javafx.scene.media.MediaPlayer mediaPlayer = new javafx.scene.media.MediaPlayer(
                    new javafx.scene.media.Media(uri)
                );
                
                // Limpiar archivo cuando termine
                mediaPlayer.setOnEndOfMedia(() -> {
                    mediaPlayer.dispose();
                    tempFile.delete();
                });
                
                mediaPlayer.play();
            } catch (Exception e) {
                System.out.println(">>> [TTS] Error reproduciendo audio: " + e.getMessage());
            }
        });
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
