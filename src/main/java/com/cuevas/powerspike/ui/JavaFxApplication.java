package com.cuevas.powerspike.ui;

import com.cuevas.powerspike.PowerspikeApplication;
import com.cuevas.powerspike.service.GameStateService;
import com.cuevas.powerspike.ui.overlay.OverlayControlBar;
import com.cuevas.powerspike.ui.overlay.OverlayStage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class JavaFxApplication extends Application {

    private static ConfigurableApplicationContext springContext;
    private Stage primaryStage;

    @Override
    public void init() {
        springContext = SpringApplication.run(PowerspikeApplication.class);
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();
        Scene scene = new Scene(root, 950, 720);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        stage.setTitle("PowerSpike");
        stage.setMinWidth(750);
        stage.setMinHeight(550);
        stage.setScene(scene);
        stage.show();

        setupOverlay();
    }

    private void setupOverlay() {
        OverlayStage overlay = springContext.getBean(OverlayStage.class);
        OverlayControlBar controlBar = springContext.getBean(OverlayControlBar.class);
        
        // Inicializar los stages en el JavaFX Application Thread
        overlay.init();
        controlBar.init();
        
        controlBar.setOverlayStage(overlay);

        // F5 toggle
        primaryStage.getScene().setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F5) {
                controlBar.toggle();
            }
        });

        // Auto-show en InProgress
        GameStateService gameState = springContext.getBean(GameStateService.class);
        gameState.inGameProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                if (newVal) {
                    controlBar.showAll();
                } else {
                    controlBar.hideAll();
                }
            });
        });
    }

    @Override
    public void stop() {
        springContext.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
