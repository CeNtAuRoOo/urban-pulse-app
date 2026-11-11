package es.grupo18.urbanpulse.maps.api;

import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.maps.dto.MapIncidentsDTO;
import es.grupo18.urbanpulse.maps.service.MapQuery;
import es.grupo18.urbanpulse.maps.service.MapQueryService;
import es.grupo18.urbanpulse.shared.error.ProblemDetailFactory;
import es.grupo18.urbanpulse.shared.ratelimit.InMemoryRateLimiter;
import es.grupo18.urbanpulse.shared.ratelimit.RateLimitProperties.Policy;
import es.grupo18.urbanpulse.shared.security.DatabaseJwtAuthenticationConverter;

@RestController
@RequestMapping("/api/v1/map/incidents")
@PreAuthorize("hasAuthority('EMAIL_VERIFIED')")
@RequiredArgsConstructor
public class MapController {
    private static final Set<String> PARAMETERS = Set.of("minLat", "minLon", "maxLat", "maxLon", "zoom",
            "status", "categoryId");
    private static final Policy RATE = new Policy(60, 60);
    private final MapQueryService service;
    private final InMemoryRateLimiter limiter;
    private final ProblemDetailFactory problems;

    @GetMapping
    public ResponseEntity<?> get(@AuthenticationPrincipal Jwt jwt,
            @RequestParam double minLat, @RequestParam double minLon,
            @RequestParam double maxLat, @RequestParam double maxLon,
            @RequestParam int zoom, @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) UUID categoryId, @RequestParam MultiValueMap<String, String> parameters) {
        if (!PARAMETERS.containsAll(parameters.keySet()) || parameters.values().stream()
                .anyMatch(v -> v.size() != 1 || v.getFirst().isBlank())) {
            throw MapQuery.invalid("Parámetros desconocidos, repetidos o vacíos.");
        }
        if (categoryId != null && !categoryId.toString().equalsIgnoreCase(parameters.getFirst("categoryId"))) {
            throw MapQuery.invalid("Identificador de categoría no válido.");
        }
        var query = new MapQuery(minLat, minLon, maxLat, maxLon, zoom, status, categoryId);
        UUID actor = DatabaseJwtAuthenticationConverter.subject(jwt);
        var decision = limiter.acquire("maps:user:" + actor, RATE);
        if (!decision.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).cacheControl(CacheControl.noStore())
                    .header("Retry-After", Long.toString(decision.retryAfterSeconds()))
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problems.create(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                            "Demasiadas consultas de mapa.", "/api/v1/map/incidents"));
        }
        MapIncidentsDTO result = service.find(actor, DatabaseJwtAuthenticationConverter.version(jwt), query);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
