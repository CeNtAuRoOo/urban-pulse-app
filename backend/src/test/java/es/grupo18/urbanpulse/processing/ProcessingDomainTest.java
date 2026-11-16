package es.grupo18.urbanpulse.processing;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.incidents.model.Coordinates;
import es.grupo18.urbanpulse.incidents.model.Incident;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.incidents.model.Location;
import es.grupo18.urbanpulse.processing.dto.ReviewRequest;
import es.grupo18.urbanpulse.processing.model.IncidentInformationResponse;
import es.grupo18.urbanpulse.processing.model.IncidentReview;
import es.grupo18.urbanpulse.processing.model.ReviewDecision;
import es.grupo18.urbanpulse.shared.error.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessingDomainTest {
    private final Instant now = Instant.parse("2026-11-15T10:00:00Z");
    private final User owner = new User("Informante", "synthetic@example.org", "x".repeat(60));
    private final Incident incident = new Incident(owner, "Aviso válido", "Descripción suficiente", null,
            new Location(new Coordinates(0d, 0d, null)), now);

    @Test
    void nullPresenceCannotBypassDisjointReviewVariants() {
        var request = new ReviewRequest();
        request.setDecision(ReviewDecision.VALIDATE);
        request.setCategoryId(UUID.randomUUID());
        request.validate();
        request.setReason(null);
        assertThatThrownBy(request::validate).isInstanceOf(ApiException.class);
        var rejection = new ReviewRequest();
        rejection.setDecision(ReviewDecision.REJECT);
        rejection.setCategoryId(null);
        rejection.setReason("Motivo");
        assertThatThrownBy(rejection::validate).isInstanceOf(ApiException.class);
    }

    @Test
    void responseCannotTargetAnotherKindOfReviewAndPreservesUnicode() {
        var rejected = new IncidentReview(incident, owner, ReviewDecision.REJECT, "Motivo", now);
        assertThatThrownBy(() -> new IncidentInformationResponse(rejected, owner, "Respuesta", now))
                .isInstanceOf(IllegalArgumentException.class);
        var request = new IncidentReview(incident, owner, ReviewDecision.REQUEST_INFORMATION, "Datos", now);
        var response = new IncidentInformationResponse(request, owner, " 🚦".repeat(1000) + " ", now);
        assertThat(response.getIncident()).isSameAs(incident);
        assertThat(response.getMessage().codePointCount(0, response.getMessage().length())).isEqualTo(1999);
        for (String text : new String[] { null, "ab", "\u2003".repeat(3), "🚦".repeat(2001), "a\uD800b" }) {
            assertThatThrownBy(() -> new IncidentInformationResponse(request, owner, text, now))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    void aggregateNeverReopensRejectedAndDoesNotTouchOriginalReportOnResponse() {
        incident.transition(IncidentStatus.AWAITING_INFORMATION, null, now);
        assertThatThrownBy(() -> incident.transition(IncidentStatus.VALIDATED, null, now))
                .isInstanceOf(ApiException.class);
        incident.transition(IncidentStatus.REPORTED, null, now);
        assertThat(incident.getTitle()).isEqualTo("Aviso válido");
        assertThat(incident.getCategory()).isNull();
        incident.transition(IncidentStatus.REJECTED, null, now);
        for (IncidentStatus target : IncidentStatus.values()) {
            assertThatThrownBy(() -> incident.transition(target, null, now)).isInstanceOf(ApiException.class);
        }
    }
}
