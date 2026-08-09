package com.cuevas.powerspike;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Contexto de Spring del frontend. Se usa únicamente como contenedor de
 * inyección de dependencias (@Service/@Component) para los clientes de LCU,
 * Live Client y los clientes HTTP hacia el backend. No levanta ningún
 * servidor web (WebApplicationType.NONE): la UI la maneja JavaFX
 * (ver JavaFxApplication).
 */
@SpringBootApplication
@EnableScheduling
public class PowerspikeFrontendApplication {

    public static ConfigurableApplicationContext run(String[] args) {
        return new SpringApplicationBuilder(PowerspikeFrontendApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
