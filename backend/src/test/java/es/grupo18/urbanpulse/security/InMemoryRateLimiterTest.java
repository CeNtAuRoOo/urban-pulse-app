package es.grupo18.urbanpulse.security;

import java.util.concurrent.atomic.AtomicLong;
import es.grupo18.urbanpulse.config.RateLimitProperties;
import es.grupo18.urbanpulse.config.RateLimitProperties.Policy;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InMemoryRateLimiterTest {
    @Test
    void refillsWithTimeAndBoundsMemoryWithoutEvictingLiveKeys() {
        AtomicLong time = new AtomicLong();
        Policy policy = new Policy(2, 10);
        var limiter = new InMemoryRateLimiter(new RateLimitProperties(policy, policy, policy, 2), time::get);
        assertThat(limiter.acquire("a", policy).allowed()).isTrue();
        assertThat(limiter.acquire("a", policy).allowed()).isTrue();
        assertThat(limiter.acquire("a", policy).retryAfterSeconds()).isEqualTo(5);
        time.set(4_000_000_000L);
        assertThat(limiter.acquire("a", policy).allowed()).isFalse();
        time.set(5_000_000_000L);
        assertThat(limiter.acquire("a", policy).allowed()).isTrue();
        limiter.acquire("b", policy);
        assertThat(limiter.acquire("c", policy).allowed()).isFalse();
        assertThat(limiter.size()).isEqualTo(2);
        assertThat(limiter.acquire("a", policy).allowed()).isFalse();
        time.set(26_000_000_000L);
        assertThat(limiter.acquire("c", policy).allowed()).isTrue();
        assertThat(limiter.size()).isEqualTo(1);
    }

    @Test
    void routeAndAccountBucketsAreIndependent() {
        Policy policy = new Policy(1, 60);
        var limiter = new InMemoryRateLimiter(new RateLimitProperties(policy, policy, policy, 10));
        assertThat(limiter.acquire("login:a", policy).allowed()).isTrue();
        assertThat(limiter.acquire("register:a", policy).allowed()).isTrue();
        assertThat(limiter.acquire("login:a", policy).allowed()).isFalse();
    }
}
