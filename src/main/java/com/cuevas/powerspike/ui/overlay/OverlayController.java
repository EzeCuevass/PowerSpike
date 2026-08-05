package com.cuevas.powerspike.ui.overlay;

import com.cuevas.powerspike.service.analysis.AnalysisEngine;
import com.cuevas.powerspike.service.analysis.AnalysisResult;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

/**
 * Controller del overlay. Se suscribe al AnalysisEngine para mostrar
 * los consejos de la IA. Cada consejo se muestra 15s y luego hace fade out.
 */
@Component
public class OverlayController {

    private static final double ADVICE_DISPLAY_SECONDS = 15;
    private static final double FADE_SECONDS = 0.3;

    @FXML private VBox overlayRoot;
    @FXML private Label adviceLabel;

    private final AnalysisEngine analysisEngine;
    private PauseTransition hideTimer;
    private FadeTransition fadeOut;

    public OverlayController(AnalysisEngine analysisEngine) {
        this.analysisEngine = analysisEngine;
    }

    @FXML
    public void initialize() {
        hideTimer = new PauseTransition(Duration.seconds(ADVICE_DISPLAY_SECONDS));
        hideTimer.setOnFinished(e -> fadeOutAdvice());

        fadeOut = new FadeTransition(Duration.seconds(FADE_SECONDS), adviceLabel);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> {
            adviceLabel.setVisible(false);
            adviceLabel.setManaged(false);
            requestResize();
        });

        analysisEngine.latestResultProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.success() && newVal.response() != null) {
                Platform.runLater(() -> showAdvice(newVal.response()));
            }
        });
    }

    private void showAdvice(String text) {
        adviceLabel.setText(text);
        adviceLabel.setOpacity(1.0);
        adviceLabel.setVisible(true);
        adviceLabel.setManaged(true);
        adviceLabel.applyCss();
        adviceLabel.layout();
        requestResize();
        hideTimer.playFromStart();
    }

    private void fadeOutAdvice() {
        fadeOut.playFromStart();
    }

    private boolean resizing = false;

    /**
     * Notifica al stage padre que debe recalcular su tamaño.
     */
    private void requestResize() {
        if (resizing) return;
        resizing = true;
        try {
            if (overlayRoot != null && overlayRoot.getScene() != null) {
                overlayRoot.getScene().getWindow().sizeToScene();
            }
        } finally {
            resizing = false;
        }
    }

    public VBox getRoot() {
        return overlayRoot;
    }
}
