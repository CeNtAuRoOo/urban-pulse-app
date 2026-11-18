package es.grupo18.urbanpulse.duplicates.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.grupo18.urbanpulse.audit.repository.AuditEventRepository;
import es.grupo18.urbanpulse.duplicates.dto.DuplicateLinkRequest;
import es.grupo18.urbanpulse.duplicates.repository.DuplicateCandidateRepository;
import es.grupo18.urbanpulse.duplicates.repository.DuplicateLinkRepository;
import es.grupo18.urbanpulse.identity.repository.UserRepository;
import es.grupo18.urbanpulse.incidents.repository.IncidentRepository;
import es.grupo18.urbanpulse.shared.error.ApiException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateServiceTest {
    @Mock
    IncidentRepository incidents;
    @Mock
    DuplicateCandidateRepository candidates;
    @Mock
    DuplicateLinkRepository links;
    @Mock
    UserRepository users;
    @Mock
    AuditEventRepository audit;
    @Mock
    DuplicateScorer scorer;

    @Test
    void rejectsSelfLinkBeforeAccessingPersistenceForTheLink() {
        UUID id = UUID.randomUUID();
        var incident = org.mockito.Mockito.mock(es.grupo18.urbanpulse.incidents.model.Incident.class);
        when(incidents.findById(id)).thenReturn(Optional.of(incident));
        when(incident.getId()).thenReturn(id);
        var service = new DuplicateService(incidents, candidates, links, users, audit, scorer,
                Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC));

        ApiException error = assertThrows(ApiException.class,
                () -> service.link(id, UUID.randomUUID(), new DuplicateLinkRequest(id, null)));
        assertEquals("DUPLICATE_SELF_LINK", error.code());
        verify(links, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnAlreadyConfirmedSymmetricPair() {
        UUID firstId = UUID.randomUUID(), secondId = UUID.randomUUID();
        var first = org.mockito.Mockito.mock(es.grupo18.urbanpulse.incidents.model.Incident.class);
        var second = org.mockito.Mockito.mock(es.grupo18.urbanpulse.incidents.model.Incident.class);
        when(incidents.findById(firstId)).thenReturn(Optional.of(first));
        when(incidents.findById(secondId)).thenReturn(Optional.of(second));
        when(first.getId()).thenReturn(firstId);
        when(second.getId()).thenReturn(secondId);
        when(links.existsBetween(firstId, secondId)).thenReturn(true);
        var service = new DuplicateService(incidents, candidates, links, users, audit, scorer,
                Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC));

        ApiException error = assertThrows(ApiException.class,
                () -> service.link(firstId, UUID.randomUUID(), new DuplicateLinkRequest(secondId, null)));
        assertEquals("DUPLICATE_LINK_EXISTS", error.code());
        verify(users, never()).findUserWithRolesById(any());
        verify(links, never()).saveAndFlush(any());
    }
}
