package es.grupo18.urbanpulse.incidents.model;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.shared.error.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentTest {
    private final Instant now = Instant.parse("2026-11-05T10:00:00Z");
    private final User reporter = new User("Ciudadano", "synthetic@example.org", "x".repeat(60));
    private final Location position = new Location(new Coordinates(90.0, -180.0, null));

    @Test
    void initialStateAndNoOpPreserveUnassignedValuesAndDates() {
        var incident = new Incident(reporter, "  Bache grande  ", "  Descripción suficiente  ", null, position, now);
        assertThat(incident.getTitle()).isEqualTo("Bache grande");
        assertThat(incident.getDescription()).isEqualTo("Descripción suficiente");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.REPORTED);
        assertThat(incident.getPriority()).isNull();
        assertThat(incident.getAssignedDepartment()).isNull();
        assertThat(incident.getResolvedAt()).isNull();
        assertThat(incident.getClosedAt()).isNull();
        assertThat(incident.correct(" Bache grande ", "Descripción suficiente", null, position, now.plusSeconds(3)))
                .isFalse();
        assertThat(incident.getUpdatedAt()).isEqualTo(now);
        assertThat(incident.correct("Otro bache", incident.getDescription(), null, position, now.plusSeconds(3)))
                .isTrue();
        assertThat(incident.getUpdatedAt()).isEqualTo(now.plusSeconds(3));
    }

    @Test
    void unicodeLimitsCountCodePointsAndPreserveInteriorFormatting() {
        assertThat(Incident.normalizeText(" \t" + "🚦".repeat(150) + "\n", 5, 150)).isEqualTo("🚦".repeat(150));
        assertThat(Incident.normalizeText("🚦".repeat(5000), 10, 5000)).hasSize(10000);
        assertThat(Incident.normalizeText("  texto\n  interior  ", 5, 150)).isEqualTo("texto\n  interior");
        for (String value : List.of("abcd", "🚦".repeat(151), "\u2003".repeat(20), "abc\u0000def", "abc\uD800def")) {
            assertThatThrownBy(() -> Incident.normalizeText(value, 5, 150)).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> Incident.normalizeText(null, 5, 150)).isInstanceOf(ApiException.class);
    }

    @Test
    void coordinateBoundsFinitudeAndSignedZero() {
        assertThat(new Coordinates(-90.0, 180.0, 0.0).getLongitude()).isEqualTo(180);
        assertThat(new Coordinates(-0.0, 0.0, -0.0)).isEqualTo(new Coordinates(0.0, -0.0, 0.0));
        for (Double value : new Double[] { null, 91.0, -91.0, Double.NaN, Double.POSITIVE_INFINITY }) {
            assertThatThrownBy(() -> new Coordinates(value, 0.0, null)).isInstanceOf(ApiException.class);
        }
        for (Double value : new Double[] { null, 181.0, -181.0, Double.NaN, Double.NEGATIVE_INFINITY }) {
            assertThatThrownBy(() -> new Coordinates(0.0, value, null)).isInstanceOf(ApiException.class);
        }
        for (Double value : new Double[] { -1.0, Double.NaN, Double.POSITIVE_INFINITY }) {
            assertThatThrownBy(() -> new Coordinates(0.0, 0.0, value)).isInstanceOf(ApiException.class);
        }
    }
}
