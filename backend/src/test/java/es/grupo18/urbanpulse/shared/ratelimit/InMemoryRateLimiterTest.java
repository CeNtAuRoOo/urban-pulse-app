package es.grupo18.urbanpulse.shared.ratelimit;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import es.grupo18.urbanpulse.shared.ratelimit.RateLimitProperties.Policy;

import static org.assertj.core.api.Assertions.*;

class InMemoryRateLimiterTest {
    private RateLimitProperties properties(Policy policy, int maxKeys) {
        return new RateLimitProperties(policy, policy, policy, policy, policy, policy, policy, policy, policy, policy,
                maxKeys);
    }

    @Test
    void refillsWithTimeAndBoundsMemoryWithoutEvictingLiveKeys() {
        AtomicLong time = new AtomicLong();
        Policy policy = new Policy(2, 10);
        var limiter = new InMemoryRateLimiter(properties(policy, 2), time::get);
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
    void heterogeneousTtlsExpireShortBucketsEvenBehindLongLivedEntries() {
        AtomicLong time = new AtomicLong();
        Policy shortPolicy = new Policy(1, 60);
        Policy longPolicy = new Policy(5, 86400);
        var limiter = new InMemoryRateLimiter(properties(shortPolicy, 2), time::get);
        limiter.acquire("long", longPolicy);
        limiter.acquire("short", shortPolicy);
        assertThat(limiter.acquire("third", shortPolicy).retryAfterSeconds()).isEqualTo(60);
        time.set(60_000_000_000L);
        assertThat(limiter.acquire("third", shortPolicy).allowed()).isTrue();
        assertThat(limiter.size()).isEqualTo(2);
        // La clave larga conserva el presupuesto, aunque la corta caducó.
        for (int i = 0; i < 4; i++)
            assertThat(limiter.acquire("long", longPolicy).allowed()).isTrue();
        assertThat(limiter.acquire("long", longPolicy).allowed()).isFalse();
    }

    @Test
    void expiryIndexStaysBoundedUnderRepeatedAccessAndHighCardinality() {
        AtomicLong time = new AtomicLong();
        Policy policy = new Policy(100_000, 60);
        var limiter = new InMemoryRateLimiter(properties(policy, 10_000), time::get);
        long started = System.nanoTime();
        for (int i = 0; i < 10_000; i++)
            limiter.acquire("key:" + i, policy);
        for (int i = 0; i < 100_000; i++)
            limiter.acquire("key:" + (i % 10_000), policy);
        var index = (java.util.Set<?>) org.springframework.test.util.ReflectionTestUtils.getField(limiter,
                "expirations");
        assertThat(index).hasSize(10_000);
        assertThat(limiter.size()).isEqualTo(10_000);
        assertThat(limiter.acquire("overflow", policy).allowed()).isFalse();
        time.set(60_000_000_000L);
        assertThat(limiter.acquire("after-expiry", policy).allowed()).isTrue();
        assertThat(index).hasSize(1);
        assertThat(limiter.size()).isEqualTo(1);
        System.out.printf("Limiter: 110000 accesses / 10000 keys in %d ms%n",
                (System.nanoTime() - started) / 1_000_000);
    }

    @Test
    void routeAndAccountBucketsAreIndependent() {
        Policy policy = new Policy(1, 60);
        var limiter = new InMemoryRateLimiter(properties(policy, 10));
        assertThat(limiter.acquire("login:a", policy).allowed()).isTrue();
        assertThat(limiter.acquire("register:a", policy).allowed()).isTrue();
        assertThat(limiter.acquire("login:a", policy).allowed()).isFalse();
    }

    @Test
    void invitationPoliciesRefillGraduallyAndRemainIndependentFromAuthentication() {
        AtomicLong time = new AtomicLong();
        Policy user = new Policy(10, 900);
        Policy ip = new Policy(30, 900);
        var limiter = new InMemoryRateLimiter(properties(user, 100), time::get);
        for (int i = 0; i < 10; i++)
            assertThat(limiter.acquire("invitation-user:a", user).allowed()).isTrue();
        assertThat(limiter.acquire("invitation-user:a", user).retryAfterSeconds()).isEqualTo(90);
        for (int i = 0; i < 30; i++)
            assertThat(limiter.acquire("invitation-ip:a", ip).allowed()).isTrue();
        assertThat(limiter.acquire("invitation-ip:a", ip).retryAfterSeconds()).isEqualTo(30);
        for (String key : java.util.List.of("login-email:a", "register-ip:a", "confirm-user:a", "resend-user:a")) {
            assertThat(limiter.acquire(key, user).allowed()).isTrue();
        }
        time.set(30_000_000_000L);
        assertThat(limiter.acquire("invitation-ip:a", ip).allowed()).isTrue();
        // Retry-After redondea conservadoramente; el cálculo fraccional puede añadir un
        // segundo.
        assertThat(limiter.acquire("invitation-user:a", user).retryAfterSeconds()).isBetween(60L, 61L);
        time.set(90_000_000_000L);
        assertThat(limiter.acquire("invitation-user:a", user).allowed()).isTrue();
        assertThat(limiter.acquire("invitation-user:a", user).allowed()).isFalse();
    }
}
