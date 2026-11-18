package es.grupo18.urbanpulse.duplicates.service;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateScorerTest {
    private final DuplicateScorer scorer = new DuplicateScorer();

    @Test
    void returnsMaximumScoreForSamePlaceTimeAndCategory() {
        var result = scorer.score(36.7213, -4.4214, Instant.parse("2026-11-14T10:00:00Z"), "POTHOLE", 36.7213, -4.4214,
                Instant.parse("2026-11-14T10:05:00Z"), "POTHOLE");
        assertEquals(1.0, result.score(), 0.001);
        assertTrue(result.reasons().contains("SAME_CATEGORY"));
    }

    @Test
    void rejectsCandidatesOutsideSpatialOrTemporalWindow() {
        assertTrue(scorer.score(36.7, -4.4, Instant.now(), null, 36.71, -4.4, Instant.now(), null).score() == 0);
        assertEquals(0, scorer.score(36.7, -4.4, Instant.parse("2026-11-01T00:00:00Z"), null, 36.7, -4.4,
                Instant.parse("2026-11-14T00:00:00Z"), null).score());
    }

    @Test
    void scoresSpatialAndTemporalClosenessWithoutCategory() {
        var result = scorer.score(36.7213, -4.4214, Instant.parse("2026-11-14T10:00:00Z"), null, 36.7214, -4.4214,
                Instant.parse("2026-11-14T10:30:00Z"), null);
        assertTrue(result.score() > 0.7);
        assertTrue(result.reasons().contains("NEARBY_LOCATION"));
        assertFalse(result.reasons().contains("SAME_CATEGORY"));
    }
}
