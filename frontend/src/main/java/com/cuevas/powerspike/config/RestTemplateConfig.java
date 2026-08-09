package com.cuevas.powerspike.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate genérico (@Primary), usado por BackendApiClient y
 * AnalysisApiClient para hablar con el backend (HTTPS público, sin
 * certificados autofirmados). Distinto del `lcuRestTemplate` calificado
 * (SSL laxo) que se usa para hablar con el LCU/Live Client locales.
 */
@Configuration
public class RestTemplateConfig {
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
