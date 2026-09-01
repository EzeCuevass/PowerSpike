package com.cuevas.powerspike.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Persiste la sesión activa del invocador (login/logout) en un archivo JSON
 * local (%APPDATA%/PowerSpike/session.json), en vez de una base de datos.
 * Reemplaza a ActiveSession/ActiveSessionRepository del monolito original.
 *
 * Se usa APPDATA en vez de una ruta relativa (data/) porque, con la app
 * empaquetada (jpackage), el working directory cambia y puede no ser
 * escribible (ej. Program Files). APPDATA siempre es escribible y sobrevive
 * a actualizaciones del programa.
 *
 * A futuro esto se reemplazará por un sistema de cuentas propio de PowerSpike
 * con login contra Riot, pero por ahora es simplemente un archivo local.
 */
@Service
public class LocalSessionStore {

    private static final Logger log = LoggerFactory.getLogger(LocalSessionStore.class);
    private static final File SESSION_FILE = storeFile("session.json");

    private static File storeFile(String name) {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return new File(appData + "/PowerSpike/" + name);
        }
        // Fallback para entornos sin APPDATA (ej. Linux en dev)
        return new File("data/" + name);
    }

    private final ObjectMapper mapper = new ObjectMapper();

    public record SessionData(String puuid, String gameName, String tagLine, long profileIconId, long summonerLevel) {}

    public java.util.Optional<SessionData> load() {
        try {
            if (!SESSION_FILE.exists()) return java.util.Optional.empty();
            SessionData data = mapper.readValue(SESSION_FILE, SessionData.class);
            return java.util.Optional.ofNullable(data);
        } catch (Exception e) {
            log.warn("No se pudo leer la sesión local: {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }

    public void save(SessionData data) {
        try {
            SESSION_FILE.getParentFile().mkdirs();
            mapper.writeValue(SESSION_FILE, data);
        } catch (Exception e) {
            log.error("No se pudo guardar la sesión local: {}", e.getMessage());
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(SESSION_FILE.toPath());
        } catch (IOException e) {
            log.warn("No se pudo borrar la sesión local: {}", e.getMessage());
        }
    }
}
