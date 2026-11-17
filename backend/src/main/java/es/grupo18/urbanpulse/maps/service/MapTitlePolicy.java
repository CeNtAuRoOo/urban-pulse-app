package es.grupo18.urbanpulse.maps.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Integration seam for a future approved title projection owned by Processing. */
@Component
public class MapTitlePolicy {
    public String visibleTitle(UUID incidentId) {
        return "Incidencia " + incidentId;
    }
}
