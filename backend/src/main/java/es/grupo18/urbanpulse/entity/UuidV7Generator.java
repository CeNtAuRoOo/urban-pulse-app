package es.grupo18.urbanpulse.entity;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/** Generates RFC 9562 UUID version 7 identifiers. */
public final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7Generator() {
    }

    public static UUID next() {
        long timestampMilliseconds = Instant.now().toEpochMilli();
        long mostSignificantBits = (timestampMilliseconds << 16)
                | 0x7000L
                | RANDOM.nextInt(1 << 12);
        long leastSignificantBits = (RANDOM.nextLong() & 0x3fff_ffff_ffff_ffffL)
                | 0x8000_0000_0000_0000L;

        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
