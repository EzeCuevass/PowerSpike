package com.cuevas.powerspike.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Persiste el token JWT de autenticación en un archivo JSON local
 * (data/auth.json), separado de la sesión de Riot (data/session.json).
 */
@Service
public class AuthTokenStore {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenStore.class);
    private static final File AUTH_FILE = new File("data/auth.json");

    private final ObjectMapper mapper = new ObjectMapper();

    public record StoredAuth(String token) {}

    public java.util.Optional<String> load() {
        try {
            if (!AUTH_FILE.exists()) return java.util.Optional.empty();
            StoredAuth data = mapper.readValue(AUTH_FILE, StoredAuth.class);
            return data != null && data.token() != null && !data.token().isBlank()
                    ? java.util.Optional.of(data.token())
                    : java.util.Optional.empty();
        } catch (Exception e) {
            log.warn("No se pudo leer el token local: {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }

    public void save(String token) {
        try {
            AUTH_FILE.getParentFile().mkdirs();
            mapper.writeValue(AUTH_FILE, new StoredAuth(token));
        } catch (Exception e) {
            log.error("No se pudo guardar el token local: {}", e.getMessage());
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(AUTH_FILE.toPath());
        } catch (IOException e) {
            log.warn("No se pudo borrar el token local: {}", e.getMessage());
        }
    }
}
