package com.cuevas.powerspike;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Entry point del backend de PowerSpike. Proceso Spring Boot headless
 * (sin JavaFX): expone la API REST que consume el frontend, y es el único
 * lugar donde viven las API keys de Riot Games y OpenAI.
 */
@SpringBootApplication
@EnableScheduling
public class PowerspikeBackendApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(PowerspikeBackendApplication.class, args);
    }

    /**
     * Busca un archivo .env subiendo directorios desde dos puntos de partida
     * (el primero que lo encuentra gana):
     *   1. La ubicación real del código (jar o carpeta de clases compiladas).
     *      Esto es robusto sin importar el working directory del proceso,
     *      porque `backend/` siempre es ancestro de `build/libs/*.jar` o de
     *      `build/classes/...`/`out/production/...` (IntelliJ).
     *   2. El working directory actual (fallback, por si el .env está en un
     *      lugar distinto al módulo, ej. la raíz del repo).
     *
     * Se cargan las variables como System properties ANTES de levantar el
     * contexto de Spring. Formato esperado: `CLAVE=valor` por línea; líneas
     * vacías o que empiezan con # se ignoran.
     */
    private static void loadDotEnv() {
        Path codeLocation = resolveCodeSourceLocation();
        if (codeLocation != null && searchAndApply(codeLocation)) return;

        Path cwd = Path.of("").toAbsolutePath();
        searchAndApply(cwd);
    }

    /**
     * Usa el primer elemento de java.class.path como ancla para buscar el
     * .env. En modo `java -jar app.jar` esto es directamente el archivo jar
     * (cuyo ancestro es `backend/`). En IntelliJ/Gradle (classpath explotado)
     * es la carpeta de clases compiladas del módulo (también descendiente de
     * `backend/`). Evitamos usar getProtectionDomain().getCodeSource(),
     * que para jars de Spring Boot devuelve una URL anidada (jar:nested:...)
     * que no se puede convertir a Path.
     */
    private static Path resolveCodeSourceLocation() {
        String classPath = System.getProperty("java.class.path");
        if (classPath == null || classPath.isBlank()) return null;
        String first = classPath.split(java.io.File.pathSeparator)[0];
        try {
            return Path.of(first).toAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean searchAndApply(Path start) {
        Path dir = start;
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                applyDotEnvFile(candidate);
                return true;
            }
            dir = dir.getParent();
        }
        return false;
    }

    private static void applyDotEnvFile(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            boolean first = true;
            for (String line : lines) {
                // El archivo puede tener BOM (U+FEFF) al principio si se guardó
                // como "UTF-8 with BOM" desde algún editor; lo ignoramos.
                if (first) {
                    line = stripLeadingBom(line);
                    first = false;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int idx = trimmed.indexOf('=');
                if (idx <= 0) continue;
                String key = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim();
                // No pisar variables ya definidas explícitamente en el entorno del proceso
                // (útil para el deploy en la nube, donde las env vars del proveedor priman).
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("No se pudo leer el archivo .env en " + file + ": " + e.getMessage());
        }
    }

    private static String stripLeadingBom(String line) {
        if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }
}
