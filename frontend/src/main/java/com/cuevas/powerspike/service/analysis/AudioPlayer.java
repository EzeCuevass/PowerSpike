package com.cuevas.powerspike.service.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

/**
 * Reproduce el audio TTS que devuelve el backend en base64 dentro de
 * AnalysisResponse.audioBase64(). Reemplaza la reproducción que antes hacía
 * TtsClient directamente (ahora TtsClient vive en el backend y solo genera
 * los bytes; el backend no tiene forma de reproducir audio del lado del
 * usuario).
 */
public class AudioPlayer {

    private static final Logger log = LoggerFactory.getLogger(AudioPlayer.class);

    public static void playBase64Mp3(String audioBase64) {
        if (audioBase64 == null || audioBase64.isBlank()) return;

        javafx.application.Platform.runLater(() -> {
            try {
                byte[] audioData = Base64.getDecoder().decode(audioBase64);
                java.io.File tempFile = java.io.File.createTempFile("tts_", ".mp3");
                tempFile.deleteOnExit();
                java.nio.file.Files.write(tempFile.toPath(), audioData);

                String uri = tempFile.toURI().toString();
                javafx.scene.media.MediaPlayer mediaPlayer = new javafx.scene.media.MediaPlayer(
                        new javafx.scene.media.Media(uri)
                );

                mediaPlayer.setOnEndOfMedia(() -> {
                    mediaPlayer.dispose();
                    tempFile.delete();
                });

                mediaPlayer.play();
            } catch (Exception e) {
                log.error("Error reproduciendo audio TTS: {}", e.getMessage());
            }
        });
    }
}
