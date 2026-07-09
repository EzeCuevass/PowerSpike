package com.cuevas.powerspike.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SummonerNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleSummonerNotFound(SummonerNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(GameNotActiveException.class)
    public ResponseEntity<Map<String, String>> handleGameNotActive(GameNotActiveException e) {
        return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<Map<String, String>> handleRiotError(HttpClientErrorException e) {
        String message = switch (e.getStatusCode().value()) {
            case 403 -> "API key inválida o expirada";
            case 404 -> "Invocador no encontrado o no está en partida";
            case 429 -> "Demasiadas requests a la API de Riot, esperá un momento";
            default -> "Error de la API de Riot: " + e.getStatusText();
        };
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception e) {
        return ResponseEntity.status(500).body(Map.of("error", "Error interno del servidor"));
    }
}
