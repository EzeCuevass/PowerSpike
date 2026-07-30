package com.cuevas.powerspike.service.analysis;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class ScreenshotService {

    public String captureScreenAsBase64() {
        try {
            CompletableFuture<BufferedImage> future = new CompletableFuture<>();
            javafx.application.Platform.runLater(() -> {
                try {
                    Rectangle2D bounds = Screen.getPrimary().getBounds();
                    Robot fxRobot = new Robot();
                    WritableImage fxImage = fxRobot.getScreenCapture(null, bounds);

                    int w = (int) bounds.getWidth();
                    int h = (int) bounds.getHeight();
                    BufferedImage screenshot = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    PixelReader reader = fxImage.getPixelReader();
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            screenshot.setRGB(x, y, reader.getArgb(x, y));
                        }
                    }
                    future.complete(screenshot);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });

            BufferedImage screenshot = future.get(2, TimeUnit.SECONDS);
            if (screenshot == null) return null;

            BufferedImage resized = resizeToSquare(screenshot, 1024);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpeg", baos);
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {

            return null;
        }
    }

    private BufferedImage resizeToSquare(BufferedImage original, int maxSize) {
        double scale = (double) maxSize / Math.max(original.getWidth(), original.getHeight());
        int newW = (int) (original.getWidth() * scale);
        int newH = (int) (original.getHeight() * scale);

        BufferedImage square = new BufferedImage(maxSize, maxSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = square.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, maxSize, maxSize);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, (maxSize - newW) / 2, (maxSize - newH) / 2, newW, newH, null);
        g.dispose();
        return square;
    }
}
