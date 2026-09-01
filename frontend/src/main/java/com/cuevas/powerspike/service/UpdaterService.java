package com.cuevas.powerspike.service;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Revisa si hay una versión nueva de la app en el backend (GET /api/app/latest).
 * Si la hay, muestra un diálogo y, al aceptar, descarga el zip del release,
 * lo descomprime en la carpeta de updates y ejecuta update.bat (elevado),
 * que reinstala en silencio y relanza la app.
 *
 * El check corre al arrancar, en background, sin bloquear la UI.
 */
@Service
public class UpdaterService {

    private static final Logger log = LoggerFactory.getLogger(UpdaterService.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String currentVersion;

    public UpdaterService(RestTemplate restTemplate,
                          @Value("${backend.base-url:http://localhost:8080}") String baseUrl,
                          @Value("${app.version:0.0.0}") String currentVersion) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.currentVersion = currentVersion;
    }

    /**
     * Lanza el check de actualizaciones en segundo plano (se llama al arrancar).
     */
    public void checkForUpdatesAsync() {
        new Thread(() -> {
            try {
                checkForUpdates();
            } catch (Exception e) {
                log.debug("Check de actualizaciones falló (se ignora): {}", e.getMessage());
            }
        }).start();
    }

    /**
     * Consulta /api/app/latest. Si la versión remota es mayor, ofrece actualizar.
     * Se ejecuta en background; el diálogo se muestra en el FX thread.
     */
    public void checkForUpdates() {
        Map<String, String> latest;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> body = restTemplate.getForObject(baseUrl + "/api/app/latest", Map.class);
            latest = body;
        } catch (Exception e) {
            log.info("No hay release publicado o no se pudo consultar: {}", e.getMessage());
            return;
        }
        if (latest == null || latest.get("version") == null || latest.get("url") == null) {
            return;
        }

        String remote = latest.get("version");
        String downloadUrl = baseUrl + latest.get("url");
        if (!isNewer(remote, currentVersion)) {
            return;
        }

        log.info("Hay versión nueva: {} (actual: {})", remote, currentVersion);

        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.CONFIRMATION);
            alert.setTitle("Actualización disponible");
            alert.setHeaderText("PowerSpike " + remote);
            alert.setContentText("Hay una versión nueva disponible (" + remote
                    + "). ¿Querés actualizar ahora?\n\nLa app se va a cerrar y volver a abrir sola cuando termine.");
            if (alert.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isPresent()) {
                downloadAndUpdate(remote, downloadUrl);
            }
        });
    }

    /**
     * Descarga el zip, lo descomprime en la carpeta de updates y ejecuta
     * update.bat con elevación (UAC), luego cierra la app.
     */
    private void downloadAndUpdate(String version, String downloadUrl) {
        Platform.runLater(() -> {
            try {
                String appData = System.getenv("APPDATA");
                String updatesDir = (appData != null && !appData.isBlank())
                        ? appData + "/PowerSpike/updates"
                        : "data/updates";
                Path dir = Path.of(updatesDir);
                Files.createDirectories(dir);

                File zipFile = dir.resolve("PowerSpike-" + version + ".zip").toFile();
                log.info("Descargando {} → {}", downloadUrl, zipFile);
                try (InputStream in = new URL(downloadUrl).openStream();
                     FileOutputStream out = new FileOutputStream(zipFile)) {
                    in.transferTo(out);
                }

                unzip(zipFile, dir.toFile());

                File batch = dir.resolve("update.bat").toFile();
                if (!batch.exists()) {
                    log.warn("update.bat no está en el release, no se puede actualizar automáticamente.");
                    return;
                }

                // Ejecutar update.bat con elevación y cerrar la app
                ProcessBuilder pb = new ProcessBuilder(
                        "powershell.exe",
                        "-Command",
                        "Start-Process -FilePath '" + batch.getAbsolutePath() + "' -Verb RunAs -WorkingDirectory '" + dir + "'");
                pb.start();
            } catch (Exception e) {
                log.error("Error en la actualización: {}", e.getMessage());
            }
        });
    }

    /**
     * Descomprime un zip a un directorio destino.
     */
    private void unzip(File zip, File destDir) throws Exception {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(zip.toPath()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = destDir.toPath().resolve(entry.getName()).normalize();
                if (!outPath.startsWith(destDir.toPath())) continue; // zip slip
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zis, outPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Compara versiones "X.Y.Z" numéricamente (0.1.9 < 0.1.10).
     */
    private boolean isNewer(String remote, String current) {
        int[] r = parse(remote);
        int[] c = parse(current);
        for (int i = 0; i < 3; i++) {
            if (r[i] != c[i]) return r[i] > c[i];
        }
        return false;
    }

    private int[] parse(String v) {
        int[] out = new int[3];
        String[] parts = v.trim().split("\\.");
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}