package com.cuevas.powerspike.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

/**
 * Endpoints de versión/descarga de la app desktop.
 *
 * - GET /api/app/latest           → lee /opt/powerspike/releases/version.json y devuelve
 *                                   { version, url } (o 404 si no hay release público).
 * - GET /api/app/download/{file}  → sirve el archivo desde el directorio de releases.
 *
 * version.json lo publica el flujo de release (manual por ahora, workflow después).
 */
@RestController
@RequestMapping("/api/app")
public class AppController {

    private final ObjectMapper mapper = new ObjectMapper();
    private final String releasesDir;

    public AppController(@Value("${app.releases-dir:/opt/powerspike/releases}") String releasesDir) {
        this.releasesDir = releasesDir;
    }

    @GetMapping("/latest")
    public ResponseEntity<?> latest() {
        File versionFile = new File(releasesDir, "version.json");
        if (!versionFile.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No hay release publicado."));
        }
        try {
            JsonNode json = mapper.readTree(versionFile);
            String version = json.path("version").asText("");
            String file = json.path("file").asText("");
            if (version.isBlank() || file.isBlank()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "version.json incompleto."));
            }
            String url = "/api/app/download/" + file;
            return ResponseEntity.ok(Map.of(
                    "version", version,
                    "url", url
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo leer version.json: " + e.getMessage()));
        }
    }

    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        File file = new File(releasesDir, fileName);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.length())
                .body(resource);
    }
}