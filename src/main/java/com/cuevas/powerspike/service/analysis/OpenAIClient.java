package com.cuevas.powerspike.service.analysis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Cliente para la API de OpenAI.
 *
 * Envía prompts al modelo gpt-5.4-mini y retorna la respuesta.
 * La API key se configura en application.properties (openai.api.key).
 *
 * Usa RestTemplate con un Map genérico para evitar dependencias de DTOs específicos.
 * El system prompt define el rol de coach de LoL con tono coloquial argentino.
 */
@Service
public class OpenAIClient {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-5.6-luna";

    // System prompt: define el comportamiento de la IA para todos los análisis
    private static final String SYSTEM_PROMPT = """
            Sos un coach experto de League of Legends. Tu trabajo es analizar la partida del jugador y darle consejos prácticos y accionables.
            
            Reglas:
            - Sé conciso y directo (máximo 4-5 líneas por consejo)
            - Usá lenguaje coloquial argentino (vos, tenés, hacé)
            - Mencioná los items y builds con sus nombres en español (ej: "Filo del Infinito", no "Infinity Edge")
            - Priorizá consejos accionables sobre teoría general
            - Si preguntan sobre un matchup, explicá cómo jugarlo en early/mid/late
            - Para post-game, damé 3 áreas concretas de mejora
            - No seas condescendiente, tratá al jugador como alguien que quiere mejorar
            - No preguntes nada al final ni ofrezcas hacer otra cosa. Terminá con el último consejo y nada más.
            - Interpretá las stats según el rol: support no necesita CS alto (el oro viene del item de support y asistencias), jungle se mide por objetivos y ganks, ADC y mid sí necesitan farmear.
            - No incluyas caracteres como asteriscos, guiones ni emojis en la respuesta. Solo texto plano.
            - Usá saltos de línea para separar secciones y que la respuesta sea legible. No escribas todo en un solo párrafo.
            """;

    private final RestTemplate restTemplate;
    private final String apiKey;

    public OpenAIClient(RestTemplate restTemplate,
                        @Value("${openai.api.key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Envía un prompt con una imagen (screenshot de la muerte) a GPT-4o para análisis visual.
     * Usa detail: "low" para procesar en 512x512 y reducir costo.
     */
    public String chatWithImage(String userPrompt, String imageBase64) {
        if (apiKey == null || apiKey.isBlank()) {
            return "[Error] API key de OpenAI no configurada.";
        }
        if (imageBase64 == null) {
            return chat(userPrompt);
        }

        try {


            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> body = Map.of(
                "model", MODEL,
                "messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", userPrompt),
                        Map.of("type", "image_url", "image_url", 
                            Map.of("url", imageBase64, "detail", "high"))
                    ))
                ),
                "max_completion_tokens", 500,
                "reasoning_effort", "none"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String rawJson = restTemplate.postForObject(API_URL, request, String.class);

            // Parse manual para extraer el content
            return extractContentFromString(rawJson);
        } catch (Exception e) {
            return "[Error] " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
    public String chat(String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            return "[Error] API key de OpenAI no configurada. Agregala en application.properties";
        }

        try {

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            // Body del request: modelo, mensajes (system + user), límites de tokens
            Map<String, Object> body = Map.of(
                "model", MODEL,
                "messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", userPrompt)
                ),
                "max_completion_tokens", 500,
                "reasoning_effort", "none"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String rawJson = restTemplate.postForObject(API_URL, request, String.class);

            return extractContentFromString(rawJson);
        } catch (Exception e) {
            return "[Error] " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /**
     * Extrae el contenido de la respuesta JSON cruda.
     */
    private String extractContentFromString(String rawJson) {
        if (rawJson == null) return "[Error] Respuesta vacía";
        try {
            int idx = rawJson.indexOf("\"content\":");
            if (idx < 0) return "[Error] No se encontró 'content' en la respuesta";
            idx += 11; // Saltar "content":
            while (idx < rawJson.length() && rawJson.charAt(idx) == ' ') idx++;
            if (rawJson.charAt(idx) == '"') {
                idx++;
                StringBuilder sb = new StringBuilder();
                while (idx < rawJson.length()) {
                    if (rawJson.charAt(idx) == '\\') {
                        idx++;
                        if (idx < rawJson.length()) {
                            char c = rawJson.charAt(idx);
                            if (c == 'n') sb.append('\n');
                            else if (c == 't') sb.append('\t');
                            else if (c == '"') sb.append('"');
                            else if (c == '\\') sb.append('\\');
                            else sb.append(c);
                            idx++;
                        }
                        continue;
                    }
                    if (rawJson.charAt(idx) == '"') break;
                    sb.append(rawJson.charAt(idx));
                    idx++;
                }
                return sb.toString();
            }
            return "[Error] content no es string";
        } catch (Exception e) {
            return "[Error] Parse: " + e.getMessage();
        }
    }
    /**
     * Verifica si la API key está configurada.
     * Se usa en el AnalysisEngine para evitar llamar a la API si no está lista.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
