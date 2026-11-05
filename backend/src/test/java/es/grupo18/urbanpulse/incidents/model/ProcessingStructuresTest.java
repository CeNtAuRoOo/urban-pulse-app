package es.grupo18.urbanpulse.incidents.model;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.shared.error.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessingStructuresTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final User actor = new User("Usuario", "synthetic@example.org", "x".repeat(60));
    private final Incident incident = new Incident(actor, "Aviso válido", "Descripción suficiente", null,
            new Location(new Coordinates(0.0, 0.0, null)), now);

    @Test
    void reviewsRequireReasonForRejectionAndInformationAndValidateOptionalReason() {
        for (ReviewDecision decision : ReviewDecision.values()) {
            for (String text : List.of("ab", " ", "\u2003".repeat(3), "🚦".repeat(2001))) {
                assertThatThrownBy(() -> new IncidentReview(incident, actor, decision, text, now))
                        .isInstanceOf(ApiException.class);
            }
            assertThat(new IncidentReview(incident, actor, decision, " abc ", now).getReason()).isEqualTo("abc");
            assertThat(new IncidentReview(incident, actor, decision, "🚦".repeat(2000), now).getReason())
                    .hasSize(4000);
        }
        for (ReviewDecision decision : List.of(ReviewDecision.REJECT, ReviewDecision.REQUEST_INFORMATION)) {
            assertThatThrownBy(() -> new IncidentReview(incident, actor, decision, null, now))
                    .isInstanceOf(ApiException.class);
        }
        assertThat(new IncidentReview(incident, actor, ReviewDecision.VALIDATE, null, now).getReason()).isNull();
    }

    @Test
    void transitionsBoundOptionalTextsWithoutInventingBusinessRequirements() {
        var change = transition("🚦".repeat(2000), "🚦".repeat(5000));
        assertThat(change.getReason()).hasSize(4000);
        assertThat(change.getResolutionNotes()).hasSize(10000);
        assertThat(transition(null, null).getChangedBy()).isNull();
        assertThat(transition(" ", "").getReason()).isEqualTo(" ");
        assertThatThrownBy(() -> transition("🚦".repeat(2001), null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> transition(null, "🚦".repeat(5001))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new StatusChange(incident, null, IncidentStatus.REPORTED, actor, null, null, now))
                .isInstanceOf(NullPointerException.class);
        for (IncidentStatus target : IncidentStatus.values()) {
            assertThatThrownBy(() -> new StatusChange(incident, IncidentStatus.CLOSED, target, actor, null, null, now))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new StatusChange(incident, IncidentStatus.REPORTED, IncidentStatus.REPORTED,
                actor, null, null, now)).isInstanceOf(IllegalArgumentException.class);
        assertThat(List.of(IncidentStatus.values())).extracting((IncidentStatus status) -> status.name())
                .doesNotContain("REOPENED");
    }

    @Test
    void prioritiesRequireNormalizedJustificationAndMandatoryReferences() {
        for (String text : new String[] { null, "", "ab", "\u2003".repeat(3), "🚦".repeat(2001) }) {
            assertThatThrownBy(() -> priority(text)).isInstanceOf(ApiException.class);
        }
        assertThat(priority(" abc ").getJustification()).isEqualTo("abc");
        assertThat(priority("🚦".repeat(2000)).getJustification()).hasSize(4000);
        assertThatThrownBy(() -> new PriorityDecision(incident, null, IncidentPriority.HIGH,
                PrioritySource.MANUAL, "abc", now)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PriorityDecision(incident, actor, null,
                PrioritySource.MANUAL, "abc", now)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PriorityDecision(incident, actor, IncidentPriority.HIGH,
                null, "abc", now)).isInstanceOf(NullPointerException.class);
    }

    private StatusChange transition(String reason, String notes) {
        return new StatusChange(incident, IncidentStatus.RESOLVED, IncidentStatus.WORK_ASSIGNED,
                null, reason, notes, now);
    }

    private PriorityDecision priority(String text) {
        return new PriorityDecision(incident, actor, IncidentPriority.HIGH, PrioritySource.MANUAL, text, now);
    }
}
