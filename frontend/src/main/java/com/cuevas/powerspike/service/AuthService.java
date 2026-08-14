package com.cuevas.powerspike.service;

import com.cuevas.powerspike.dto.AuthResponse;
import com.cuevas.powerspike.dto.LoginRequest;
import com.cuevas.powerspike.dto.RegisterRequest;
import com.cuevas.powerspike.dto.UserDTO;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Gestiona la autenticación contra el backend:
 * - login y register (ambos devuelven un token JWT del backend).
 * - persistencia del token en data/auth.json (AuthTokenStore).
 * - restoreSession(): auto-login silencioso al arrancar si hay token válido.
 * - logout(): borra token + sesión de Riot.
 *
 * Expone `loggedIn` (BooleanProperty) para que la UI muestre/oculte el panel
 * de login. Devuelve mensajes de error (o null si ok) para mostrarlos en la UI.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final AuthTokenStore tokenStore;
    private final LocalSessionStore sessionStore;

    private final BooleanProperty loggedIn = new SimpleBooleanProperty(false);
    private final StringProperty currentMail = new SimpleStringProperty("");

    private volatile String token;

    public AuthService(RestTemplate restTemplate,
                       @Value("${backend.base-url:http://localhost:8080}") String baseUrl,
                       AuthTokenStore tokenStore,
                       LocalSessionStore sessionStore) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.tokenStore = tokenStore;
        this.sessionStore = sessionStore;
    }

    public String getToken() {
        return token;
    }

    public boolean isLoggedIn() {
        return loggedIn.get();
    }

    public BooleanProperty loggedInProperty() {
        return loggedIn;
    }

    public StringProperty currentMailProperty() {
        return currentMail;
    }

    /**
     * Intenta loguear. Devuelve null si ok, o un mensaje de error.
     */
    public String login(String mail, String password) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<LoginRequest> entity = new HttpEntity<>(new LoginRequest(mail, password), headers);

            ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                    baseUrl + "/api/users/login", entity, AuthResponse.class);

            AuthResponse body = response.getBody();
            if (body != null && body.token() != null) {
                setSession(body.token(), body.user());
                return null;
            }
            return "Respuesta inesperada del backend.";
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return "Credenciales inválidas.";
            }
            return "Error: " + e.getStatusCode() + " - " + e.getStatusText();
        } catch (Exception e) {
            log.warn("Login falló: {}", e.getMessage());
            return "No se pudo conectar con el backend.";
        }
    }

    /**
     * Registra y loguea automáticamente. Devuelve null si ok, o un mensaje de error.
     */
    public String register(String mail, String username, String password) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RegisterRequest> entity = new HttpEntity<>(new RegisterRequest(mail, username, password), headers);

            ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                    baseUrl + "/api/users/register", entity, AuthResponse.class);

            AuthResponse body = response.getBody();
            if (body != null && body.token() != null) {
                setSession(body.token(), body.user());
                return null;
            }
            return "Respuesta inesperada del backend.";
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                return "El mail o el username ya están en uso.";
            }
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                return "Datos inválidos.";
            }
            return "Error: " + e.getStatusCode() + " - " + e.getStatusText();
        } catch (Exception e) {
            log.warn("Registro falló: {}", e.getMessage());
            return "No se pudo conectar con el backend.";
        }
    }

    /**
     * Auto-login silencioso al arrancar: si hay token guardado, valida con
     * GET /api/users/me. Se ejecuta en background y actualiza `loggedIn`.
     */
    public void restoreSession() {
        tokenStore.load().ifPresent(savedToken -> {
            this.token = savedToken;
            new Thread(() -> {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Authorization", "Bearer " + savedToken);
                    HttpEntity<Void> entity = new HttpEntity<>(headers);

                    ResponseEntity<UserDTO> response = restTemplate.exchange(
                            baseUrl + "/api/users/me", HttpMethod.GET, entity, UserDTO.class);

                    if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                        Platform.runLater(() -> {
                            currentMail.set(response.getBody().mail());
                            loggedIn.set(true);
                        });
                    } else {
                        clearToken();
                    }
                } catch (Exception e) {
                    log.info("Auto-login falló (token inválido/expirado): {}", e.getMessage());
                    clearToken();
                }
            }).start();
        });
    }

    /**
     * Logout: borra token de cuenta + sesión de Riot.
     */
    public void logout() {
        clearToken();
        sessionStore.clear();
    }

    /**
     * Llamado por los clientes HTTP cuando reciben un 401 (token vencido).
     * Vuelve al estado deslogueado y muestra el panel de login.
     */
    public void handleUnauthorized() {
        clearToken();
    }

    private void setSession(String token, UserDTO user) {
        this.token = token;
        tokenStore.save(token);
        Platform.runLater(() -> {
            currentMail.set(user != null ? user.mail() : "");
            loggedIn.set(true);
        });
    }

    private void clearToken() {
        this.token = null;
        tokenStore.clear();
        Platform.runLater(() -> {
            currentMail.set("");
            loggedIn.set(false);
        });
    }
}
