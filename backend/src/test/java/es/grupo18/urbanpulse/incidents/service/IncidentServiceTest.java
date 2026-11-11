package es.grupo18.urbanpulse.incidents.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import es.grupo18.urbanpulse.audit.repository.AuditEventRepository;
import es.grupo18.urbanpulse.categories.repository.CategoryRepository;
import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.identity.repository.UserRepository;
import es.grupo18.urbanpulse.incidents.dto.UpdateIncidentRequest;
import es.grupo18.urbanpulse.incidents.model.Coordinates;
import es.grupo18.urbanpulse.incidents.model.Incident;
import es.grupo18.urbanpulse.incidents.model.Location;
import es.grupo18.urbanpulse.incidents.repository.IncidentRepository;
import es.grupo18.urbanpulse.shared.error.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IncidentServiceTest {
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final AuditEventRepository audit = mock(AuditEventRepository.class);
    private final UUID actorId = UUID.randomUUID();
    private final UUID incidentId = UUID.randomUUID();
    private final User actor = mock(User.class);
    private final tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
            .build();
    private final IncidentService service = new IncidentService(incidents, new IncidentAccess(users, incidents),
            categories, audit,
            Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC), mapper);

    @BeforeEach
    void activeCitizen() {
        when(actor.getId()).thenReturn(actorId);
        when(actor.isActive()).thenReturn(true);
        when(actor.isEmailVerified()).thenReturn(true);
        when(actor.globalRoles()).thenReturn(Set.of(RoleCode.CITIZEN));
        when(users.findUserWithRolesById(actorId)).thenReturn(Optional.of(actor));
        when(users.findForEmailVerification(actorId)).thenReturn(Optional.of(actor));
    }

    @Test
    void serviceItselfRejectsGlobalListingForCitizen() {
        assertStatus(() -> service.list(actorId, 0, 1, 20, null, null, null, null), 403);
        verifyNoInteractions(incidents, audit);
    }

    @Test
    void ownershipIsCheckedBeforeMissingOrStalePrecondition() {
        var other = mock(User.class);
        when(other.getId()).thenReturn(UUID.randomUUID());
        when(incidents.findById(incidentId)).thenReturn(Optional.of(incident(other)));
        assertStatus(() -> service.patch(actorId, 0, incidentId, null, patch()), 404);
        assertStatus(() -> service.patch(actorId, 0, incidentId, "obsolete", patch()), 404);
        verifyNoInteractions(audit, categories);
    }

    @Test
    void currentSecurityStateIsRevalidatedInService() {
        assertStatus(() -> service.get(actorId, 1, incidentId), 401);
        when(actor.isEmailVerified()).thenReturn(false);
        assertStatus(() -> service.get(actorId, 0, incidentId), 403);
        when(actor.isActive()).thenReturn(false);
        assertStatus(() -> service.get(actorId, 0, incidentId), 401);
        verifyNoInteractions(incidents);
    }

    @Test
    void flushConflictBecomes412BeforeWritingAudit() {
        when(incidents.findById(incidentId)).thenReturn(Optional.of(incident(actor)));
        doThrow(new ObjectOptimisticLockingFailureException(Incident.class, incidentId)).when(incidents).flush();
        assertStatus(() -> service.patch(actorId, 0, incidentId,
                es.grupo18.urbanpulse.incidents.api.IncidentETags.format(mapper, service.get(actorId, 0, incidentId)),
                patch()), 412);
        verifyNoInteractions(audit);
    }

    private Incident incident(User reporter) {
        var result = new Incident(reporter, "Bache grande", "Descripción suficiente", null,
                new Location(new Coordinates(0.0, 0.0, null)), Instant.parse("2026-11-05T09:00:00Z"));
        ReflectionTestUtils.setField(result, "id", incidentId);
        return result;
    }

    private UpdateIncidentRequest patch() {
        var request = new UpdateIncidentRequest();
        request.setTitle("Título corregido");
        return request;
    }

    private void assertStatus(Runnable action, int expected) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status().value()).isEqualTo(expected));
    }
}
