package es.grupo18.urbanpulse.security;

import java.util.*;
import java.util.function.LongSupplier;
import es.grupo18.urbanpulse.config.RateLimitProperties;
import es.grupo18.urbanpulse.config.RateLimitProperties.Policy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Limita las peticiones mediante buckets de tokens almacenados en memoria.
 * Repone los tokens progresivamente, elimina los buckets inactivos
 * tras el periodo de reposición de su propia política y restringe las claves.
 * No mantiene cuotas compartidas entre instancias ni tras reiniciar el proceso.
 */
@Component
@RequiredArgsConstructor
public class InMemoryRateLimiter {

    private final RateLimitProperties properties;
    private final LongSupplier ticker;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>(16, 0.75f, true);

    private final NavigableSet<Bucket> expirations = new TreeSet<>(Comparator
            .comparingLong((Bucket bucket) -> bucket.expiresAt).thenComparing(bucket -> bucket.key));

    /**
     * Crea un limitador de peticiones utilizando las políticas configuradas
     * y el reloj monotónico del sistema para calcular los intervalos
     * de reposición y caducidad de los buckets.
     *
     * @param properties configuración de las políticas de limitación
     */
    @Autowired
    public InMemoryRateLimiter(RateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    /**
     * Intenta consumir un token del bucket asociado a una clave.
     * Repone los tokens según la política configurada, elimina buckets
     * inactivos y rechaza peticiones cuando no hay capacidad disponible.
     *
     * @param key    identificador del bucket que debe utilizarse
     * @param policy política de capacidad y reposición de tokens
     * @return decisión que indica si se permite la petición y cuánto esperar
     */
    public synchronized Decision acquire(String key, Policy policy) {
        long now = ticker.getAsLong();
        long ttl = Math.multiplyExact(policy.refillSeconds(), 1_000_000_000L);

        while (!expirations.isEmpty() && now >= expirations.first().expiresAt) {
            buckets.remove(expirations.pollFirst().key);
        }

        Bucket bucket = buckets.get(key);

        if (bucket == null) {
            if (buckets.size() >= properties.maxKeys()) {
                return new Decision(false, secondsCeil(expirations.first().expiresAt - now));
            }

            bucket = new Bucket(key, policy.capacity(), now);
            buckets.put(key, bucket);
        }

        expirations.remove(bucket);

        double refillNanos = policy.refillSeconds() * 1_000_000_000.0 / policy.capacity();
        bucket.tokens = Math.min(policy.capacity(), bucket.tokens + (now - bucket.lastSeen) / refillNanos);
        bucket.lastSeen = now;
        bucket.expiresAt = now + ttl;
        expirations.add(bucket);

        if (bucket.tokens >= 1) {
            bucket.tokens--;
            return new Decision(true, 0);
        }

        return new Decision(false, secondsCeil((long) Math.ceil((1 - bucket.tokens) * refillNanos)));
    }

    /**
     * Obtiene el número de buckets almacenados actualmente en memoria.
     *
     * @return cantidad de buckets existentes
     */
    public synchronized int size() {
        return buckets.size();
    }

    /**
     * Convierte un intervalo de nanosegundos a segundos, redondeando
     * hacia arriba y garantizando un resultado mínimo de un segundo.
     *
     * @param nanos intervalo de tiempo en nanosegundos
     * @return intervalo en segundos, redondeado hacia arriba
     */
    private static long secondsCeil(long nanos) {
        return Math.max(1, (long) Math.ceil(nanos / 1_000_000_000.0));
    }

    /**
     * Representa el resultado de un intento de consumir un token.
     *
     * @param allowed           indica si la petición puede continuar
     * @param retryAfterSeconds tiempo de espera en segundos si se rechaza,
     *                          o cero si se permite
     */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    /**
     * Representa un bucket de tokens asociado a una clave de limitación.
     * Almacena los tokens disponibles y el instante de su última utilización,
     * utilizado para calcular la reposición y la expiración por inactividad.
     */
    private static final class Bucket {
        private final String key;
        private double tokens;
        private long lastSeen;
        private long expiresAt;

        /**
         * Crea un bucket de tokens con su identificador, capacidad inicial
         * e instante de última utilización.
         *
         * @param key    identificador del bucket
         * @param tokens cantidad inicial de tokens disponibles
         * @param now    instante de creación expresado en nanosegundos
         */
        private Bucket(String key, double tokens, long now) {
            this.key = key;
            this.tokens = tokens;
            this.lastSeen = now;
        }
    }
}
