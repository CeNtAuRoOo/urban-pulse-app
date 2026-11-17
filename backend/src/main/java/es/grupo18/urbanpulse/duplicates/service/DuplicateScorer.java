package es.grupo18.urbanpulse.duplicates.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

/** Puntuación heurística; nunca representa una probabilidad ni confirma duplicados. */
@Component
public class DuplicateScorer {
    public static final double MAX_DISTANCE_METERS = 250.0;
    public static final Duration MAX_TIME_DIFFERENCE = Duration.ofHours(72);
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    public record Score(double score, List<String> reasons) {}

    /** Calcula similitud espacial/temporal y añade categoría cuando ambas están disponibles. */
    public Score score(double latA, double lonA, Instant timeA, String categoryA,
            double latB, double lonB, Instant timeB, String categoryB) {
        double distance = distanceMeters(latA, lonA, latB, lonB);
        Duration difference = Duration.between(timeA, timeB).abs();
        if (distance > MAX_DISTANCE_METERS || difference.compareTo(MAX_TIME_DIFFERENCE) > 0) {
            return new Score(0.0, List.of());
        }
        double spatial = 1.0 - distance / MAX_DISTANCE_METERS;
        double temporal = 1.0 - (double) difference.toMillis() / MAX_TIME_DIFFERENCE.toMillis();
        List<String> reasons = new ArrayList<>();
        reasons.add("NEARBY_LOCATION");
        reasons.add("CLOSE_IN_TIME");
        double value;
        if (categoryA != null && categoryB != null) {
            boolean sameCategory = Objects.equals(categoryA, categoryB);
            value = spatial * 0.6 + temporal * 0.3 + (sameCategory ? 0.1 : 0.0);
            if (sameCategory) reasons.add("SAME_CATEGORY");
        } else {
            value = spatial * (2.0 / 3.0) + temporal * (1.0 / 3.0);
        }
        return new Score(Math.max(0, Math.min(1, value)), List.copyOf(reasons));
    }

    /** Distancia geodésica Haversine en metros. */
    static double distanceMeters(double latA, double lonA, double latB, double lonB) {
        double p1 = Math.toRadians(latA), p2 = Math.toRadians(latB);
        double dp = Math.toRadians(latB - latA), dl = Math.toRadians(lonB - lonA);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        a = Math.max(0, Math.min(1, a));
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
