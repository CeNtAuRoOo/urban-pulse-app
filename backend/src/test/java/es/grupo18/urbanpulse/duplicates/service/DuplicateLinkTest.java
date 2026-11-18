package es.grupo18.urbanpulse.duplicates.service;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import es.grupo18.urbanpulse.duplicates.model.DuplicateLink;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.incidents.model.Coordinates;
import es.grupo18.urbanpulse.incidents.model.Incident;
import es.grupo18.urbanpulse.incidents.model.Location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DuplicateLinkTest {
    @Test
    void storesPairInCanonicalOrderRegardlessOfRequestOrder() throws Exception {
        Incident low = incident(UUID.fromString("00000000-0000-7000-8000-000000000001"));
        Incident high = incident(UUID.fromString("00000000-0000-7000-8000-000000000002"));
        User actor = new User("Operador", "operator@example.test", "hash");
        DuplicateLink link = new DuplicateLink(high, low, actor, Instant.parse("2026-11-14T12:00:00Z"), "mismo bache");
        assertEquals(low.getId(), link.getIncident().getId());
        assertEquals(high.getId(), link.getCandidateIncident().getId());
        assertEquals("mismo bache", link.getReason());
    }

    @Test
    void rejectsSelfLink() throws Exception {
        Incident same = incident(UUID.randomUUID());
        User actor = new User("Operador", "operator@example.test", "hash");
        assertThrows(IllegalArgumentException.class, () -> new DuplicateLink(same, same, actor, Instant.now(), null));
    }

    private static Incident incident(UUID id) throws Exception {
        User reporter = new User("Ciudadano", "citizen@example.test", "hash");
        Incident incident = new Incident(reporter, "Bache en la calle", "Hay un bache grande en la calzada.", null,
                new Location(new Coordinates(36.7, -4.4, null)), Instant.parse("2026-11-14T10:00:00Z"));
        Field field = Incident.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(incident, id);
        return incident;
    }
}
