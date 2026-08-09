package com.cuevas.powerspike;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point del backend de PowerSpike. Proceso Spring Boot headless
 * (sin JavaFX): expone la API REST que consume el frontend, y es el único
 * lugar donde viven las API keys de Riot Games y OpenAI.
 */
@SpringBootApplication
public class PowerspikeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PowerspikeBackendApplication.class, args);
    }
}
