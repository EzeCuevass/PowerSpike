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
    private static final String MODEL = "gpt-5.4-mini";

    // System prompt: define el comportamiento de la IA para todos los análisis
    private static final String SYSTEM_PROMPT = """
            Sos un coach experto de League of Legends. Tu trabajo es analizar la partida del jugador y darle consejos prácticos y accionables.
            
            Reglas:
            - Sé conciso y directo (máximo 4-5 líneas por consejo)
            - Usá lenguaje coloquial argentino (vos, tenés, hacé)
            - Priorizá consejos accionables sobre teoría general
            - Si preguntan sobre un matchup, explicá cómo jugarlo en early/mid/late
            - Para post-game, damé 3 áreas concretas de mejora
            - No seas condescendiente, tratá al jugador como alguien que quiere mejorar
            - No preguntes nada al final ni ofrezcas hacer otra cosa. Terminá con el último consejo y nada más.
            """;

    private final RestTemplate restTemplate;
    private final String apiKey;

    public OpenAIClient(RestTemplate restTemplate,
                        @Value("${openai.api.key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Envía un prompt a la API de OpenAI y retorna la respuesta.
     * Si la API key no está configurada, retorna un mensaje de error.
     *
     * @param userPrompt el prompt con el contexto del análisis
     * @return la respuesta de la IA o un mensaje de error
     */
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
                "max_completion_tokens", 500,  // Límite de tokens en la respuesta
                "temperature", 0.7             // Creatividad: 0 = determinista, 1 = más variado
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            Map<?, ?> response = restTemplate.postForObject(API_URL, request, Map.class);

            return extractContent(response);
        } catch (Exception e) {
            return "[Error] " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /**
     * Extrae el contenido del mensaje de la respuesta de OpenAI.
     * La estructura de respuesta es: { choices: [{ message: { content: "..." } }] }
     */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> response) {
        if (response == null) return "[Error] Respuesta vacía de OpenAI";

        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) return "[Error] Sin choices en la respuesta";

            Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
            if (message == null) return "[Error] Sin message en la respuesta";

            return (String) message.get("content");
        } catch (Exception e) {
            return "[Error] No se pudo parsear la respuesta: " + e.getMessage();
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
