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
 * y restringe el número máximo de claves almacenadas.
 */
@Component
@RequiredArgsConstructor
public class InMemoryRateLimiter {

    private final RateLimitProperties properties;
    private final LongSupplier ticker;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>(16, 0.75f, true);

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
        long ttl = Math.multiplyExact(properties.ttlSeconds(), 1_000_000_000L);
        var iterator = buckets.values().iterator();

        while (iterator.hasNext()) {
            if (now - iterator.next().lastSeen < ttl) {
                break;
            }

            iterator.remove();
        }

        Bucket bucket = buckets.get(key);

        if (bucket == null) {
            if (buckets.size() >= properties.maxKeys()) {
                long oldest = buckets.values().iterator().next().lastSeen;
                return new Decision(false, secondsCeil(ttl - (now - oldest)));
            }

            bucket = new Bucket(policy.capacity(), now);
            buckets.put(key, bucket);
        }

        double refillNanos = policy.refillSeconds() * 1_000_000_000.0 / policy.capacity();
        bucket.tokens = Math.min(policy.capacity(), bucket.tokens + (now - bucket.lastSeen) / refillNanos);
        bucket.lastSeen = now;

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
        private double tokens;
        private long lastSeen;

        private Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastSeen = now;
        }
    }
}
