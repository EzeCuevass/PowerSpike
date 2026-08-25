package com.cuevas.powerspike.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Filtro de rate limiting. Va DESPUÉS de JwtAuthFilter en la cadena, así puede
 * leer el userId autenticado y limitar por usuario en vez de por IP.
 *
 * Límites por categoría de endpoint:
 *  - /api/analysis/*      → 10 req/min por usuario (protege créditos de OpenAI)
 *  - /api/users/login|register → 10 req/min por IP (anti brute-force)
 *  - matches/summoner/live-game → 60 req/min por usuario (cuida límites de Riot)
 *  - /api/champions/*     → 120 req/min por IP (Data Dragon, evita sobrecarga)
 *  - resto                → 60 req/min por usuario (o IP si no autenticado)
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Bandwidth ANALYSIS_LIMIT =
            Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build();
    private static final Bandwidth AUTH_LIMIT =
            Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build();
    private static final Bandwidth RIOT_LIMIT =
            Bandwidth.builder().capacity(60).refillGreedy(60, Duration.ofMinutes(1)).build();
    private static final Bandwidth CHAMPIONS_LIMIT =
            Bandwidth.builder().capacity(120).refillGreedy(120, Duration.ofMinutes(1)).build();
    private static final Bandwidth DEFAULT_LIMIT =
            Bandwidth.builder().capacity(60).refillGreedy(60, Duration.ofMinutes(1)).build();

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        Bandwidth limit = limitFor(path);
        String key = resolveKey(request, path) + "|" + limitName(path);

        ConsumptionProbe probe = rateLimitService.tryConsume(key, limit);
        if (probe.isConsumed()) {
            // Debug temporal: verificar que el filtro corre (remover luego)
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            response.setHeader("X-RateLimit-Key", key.substring(0, Math.min(key.length(), 30)));
            filterChain.doFilter(request, response);
            return;
        }

        long retrySeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retrySeconds));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\": \"Demasiadas peticiones. Esperá un momento y reintentá.\"}");
    }

    private String limitName(String path) {
        if (path.contains("/api/analysis/")) return "analysis";
        if (path.contains("/api/users/login") || path.contains("/api/users/register")) return "auth";
        if (path.contains("/api/matches/") || path.contains("/api/summoner/") || path.contains("/api/live-game/")) {
            return "riot";
        }
        if (path.contains("/api/champions/")) return "champions";
        return "default";
    }

    private Bandwidth limitFor(String path) {
        if (path.contains("/api/analysis/")) return ANALYSIS_LIMIT;
        if (path.contains("/api/users/login") || path.contains("/api/users/register")) return AUTH_LIMIT;
        if (path.contains("/api/matches/") || path.contains("/api/summoner/") || path.contains("/api/live-game/")) {
            return RIOT_LIMIT;
        }
        if (path.contains("/api/champions/")) return CHAMPIONS_LIMIT;
        return DEFAULT_LIMIT;
    }

    /**
     * Autenticado → clave por userId (mail). Público (login/register/champions) → por IP.
     */
    private String resolveKey(HttpServletRequest request, String path) {
        boolean authEndpoint = path.contains("/api/users/login") || path.contains("/api/users/register");

        if (!authEndpoint) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserDetails userDetails) {
                return "user:" + userDetails.getUsername();
            }
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}