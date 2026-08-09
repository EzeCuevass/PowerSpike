package com.cuevas.powerspike.ui.overlay;

import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.springframework.stereotype.Component;

/**
 * Barra de control del overlay. Contiene el botón X (cerrar) y el drag handle.
 * Es clickable (no click-through) y se mueve junto al OverlayStage.
 */
@Component
public class OverlayControlBar {

    private static final String CONTROL_BAR_TITLE = "PowerSpikeControlBar";
    private static final double WIDTH = 440;
    private static final double HEIGHT = 28;
    private static final double MARGIN = 20;

    private Stage stage;
    private OverlayStage overlayStage;
    private double dragOffsetX;
    private double dragOffsetY;

    public void init() {
        HBox root = new HBox(6);
        root.getStyleClass().add("control-bar-root");
        root.setStyle("-fx-background-color: rgba(13, 17, 23, 0.92);"
                + "-fx-background-radius: 6;"
                + "-fx-border-color: rgba(88, 166, 255, 0.35);"
                + "-fx-border-radius: 6;"
                + "-fx-border-width: 1;"
                + "-fx-padding: 0 8;"
                + "-fx-alignment: center-left;");

        Button closeButton = new Button("✕");
        closeButton.setStyle("-fx-background-color: transparent;"
                + "-fx-text-fill: #f85149;"
                + "-fx-font-size: 14;"
                + "-fx-cursor: hand;"
                + "-fx-padding: 2 6;");
        closeButton.setOnMouseEntered(e -> closeButton.setStyle("-fx-background-color: rgba(248,81,73,0.2);"
                + "-fx-text-fill: #f85149;"
                + "-fx-font-size: 14;"
                + "-fx-cursor: hand;"
                + "-fx-padding: 2 6;"));
        closeButton.setOnMouseExited(e -> closeButton.setStyle("-fx-background-color: transparent;"
                + "-fx-text-fill: #f85149;"
                + "-fx-font-size: 14;"
                + "-fx-cursor: hand;"
                + "-fx-padding: 2 6;"));
        closeButton.setOnAction(e -> hideAll());

        Label dragHandle = new Label("⋮⋮");
        dragHandle.setStyle("-fx-text-fill: #8b949e;"
                + "-fx-font-size: 12;"
                + "-fx-cursor: move;"
                + "-fx-padding: 0 4;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label titleLabel = new Label("PowerSpike");
        titleLabel.setStyle("-fx-text-fill: #58a6ff;"
                + "-fx-font-size: 11;"
                + "-fx-font-weight: bold;");

        root.getChildren().addAll(closeButton, dragHandle, spacer, titleLabel);

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        scene.setFill(null);

        stage = new Stage();
        stage.setTitle(CONTROL_BAR_TITLE);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);

        positionTopRight();

        // Drag handling
        root.setOnMousePressed(e -> {
            dragOffsetX = e.getScreenX() - stage.getX();
            dragOffsetY = e.getScreenY() - stage.getY();
        });
        root.setOnMouseDragged(e -> {
            double newX = e.getScreenX() - dragOffsetX;
            double newY = e.getScreenY() - dragOffsetY;
            stage.setX(newX);
            stage.setY(newY);
            if (overlayStage != null && overlayStage.isShowing()) {
                overlayStage.setX(newX);
                overlayStage.setY(newY + HEIGHT + 4);
            }
        });
    }

    private void positionTopRight() {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMaxX() - WIDTH - MARGIN);
        stage.setY(bounds.getMinY() + MARGIN);
    }

    public void setOverlayStage(OverlayStage overlayStage) {
        this.overlayStage = overlayStage;
    }

    public void hideAll() {
        stage.hide();
        if (overlayStage != null) overlayStage.hide();
    }

    public void showAll() {
        stage.show();
        if (overlayStage != null) {
            overlayStage.show();
            overlayStage.setX(stage.getX());
            overlayStage.setY(stage.getY() + HEIGHT + 4);
        }
    }

    public void toggle() {
        if (stage.isShowing()) hideAll();
        else showAll();
    }

    public boolean isShowing() {
        return stage.isShowing();
    }
}
