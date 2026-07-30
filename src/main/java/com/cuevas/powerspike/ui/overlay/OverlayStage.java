package com.cuevas.powerspike.ui.overlay;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Stage principal del overlay. Es click-through (vía JNA) y muestra
 * solo los consejos de la IA. Se posiciona en la esquina superior derecha.
 */
@Component
public class OverlayStage {

    private static final String OVERLAY_TITLE = "PowerSpikeOverlay";
    private static final double WIDTH = 340;
    private static final double MARGIN = 20;

    private final ConfigurableApplicationContext springContext;
    private Stage stage;
    private boolean clickThroughApplied = false;

    public OverlayStage(ConfigurableApplicationContext springContext) {
        this.springContext = springContext;
    }

    public void init() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/overlay.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            Scene scene = new Scene(root, WIDTH, 60);
            scene.setFill(null);
            scene.getStylesheets().add(getClass().getResource("/css/overlay.css").toExternalForm());

            stage = new Stage();
            stage.setTitle(OVERLAY_TITLE);
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.setScene(scene);
            stage.setAlwaysOnTop(true);
            stage.setResizable(false);

            positionTopRight();

            stage.showingProperty().addListener((obs, wasShowing, isShowing) -> {
                if (isShowing && !clickThroughApplied) {
                    Platform.runLater(() -> {
                        OverlayClickThrough.apply(stage);
                        clickThroughApplied = true;
                    });
                }
            });
        } catch (Exception e) {
            System.err.println("Error inicializando overlay: " + e.getMessage());
        }
    }

    private void positionTopRight() {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMaxX() - WIDTH - MARGIN);
        stage.setY(bounds.getMinY() + MARGIN);
    }

    public void show() {
        stage.show();
    }

    public void hide() {
        stage.hide();
    }

    public void toggle() {
        if (stage.isShowing()) stage.hide();
        else stage.show();
    }

    public boolean isShowing() {
        return stage.isShowing();
    }

    public void setX(double x) {
        stage.setX(x);
    }

    public void setY(double y) {
        stage.setY(y);
    }

    public double getX() {
        return stage.getX();
    }

    public double getY() {
        return stage.getY();
    }
}
