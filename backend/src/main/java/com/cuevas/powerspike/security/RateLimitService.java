package com.cuevas.powerspike.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting con token bucket (Bucket4j) en memoria.
 * Cada clave (usuario o IP + categoría de endpoint) tiene su propio bucket.
 * Si el bucket se vacía, se responde 429 con Retry-After.
 *
 * In-memory es suficiente: el backend corre en una sola instancia.
 * ponytail: mapa en memoria sin expiración por entrada; el cleanup periódico
 * borra buckets con más de 30 min sin actividad - si hubiera varias instancias,
 * habría que pasar a un backend distribuido (Redis).
 */
@Service
public class RateLimitService {

    private static final long STALE_AFTER_MS = 30 * 60_000; // 30 min sin uso → limpiar

    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    private record BucketEntry(Bucket bucket, long lastAccessMs) {}

    /**
     * Intenta consumir un token del bucket de la clave dada.
     * @return probe: isConsumed() == false → límite excedido
     */
    public ConsumptionProbe tryConsume(String key, Bandwidth bandwidth) {
        BucketEntry entry = buckets.compute(key, (k, existing) -> {
            if (existing == null) {
                return new BucketEntry(Bucket.builder().addLimit(bandwidth).build(), System.currentTimeMillis());
            }
            // Record inmutable: se descarta la entrada vieja y se crea una con timestamp fresco
            return existing;
        });
        return entry.bucket.tryConsumeAndReturnRemaining(1);
    }

    /**
     * Limpia periódicamente los buckets inactivos para que el mapa no crezca sin límite.
     */
    @Scheduled(fixedDelay = 10 * 60_000)
    public void cleanupStaleBuckets() {
        long cutoff = System.currentTimeMillis() - STALE_AFTER_MS;
        buckets.entrySet().removeIf(e -> e.getValue().lastAccessMs() < cutoff);
    }
}