package es.grupo18.urbanpulse.processing.api;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.grupo18.urbanpulse.processing.dto.InformationResponseDTO;
import es.grupo18.urbanpulse.processing.dto.InformationResponsePageDTO;
import es.grupo18.urbanpulse.processing.dto.InformationResponseRequest;
import es.grupo18.urbanpulse.processing.dto.PriorityDecisionDTO;
import es.grupo18.urbanpulse.processing.dto.PriorityDecisionPageDTO;
import es.grupo18.urbanpulse.processing.dto.PriorityRequest;
import es.grupo18.urbanpulse.processing.dto.ReviewDTO;
import es.grupo18.urbanpulse.processing.dto.ReviewOutcomeDTO;
import es.grupo18.urbanpulse.processing.dto.ReviewPageDTO;
import es.grupo18.urbanpulse.processing.dto.ReviewRequest;
import es.grupo18.urbanpulse.processing.service.ProcessingService;
import es.grupo18.urbanpulse.shared.error.ApiException;
import es.grupo18.urbanpulse.shared.security.DatabaseJwtAuthenticationConverter;

/**
 * Diez operaciones de la primera fase; la autorización real se revalida en el servicio.
 */
@RestController
@RequestMapping("/api/v1/incidents/{incidentId}")
@PreAuthorize("hasAuthority('EMAIL_VERIFIED')")
@RequiredArgsConstructor
public class ProcessingController {

    /** Casos de uso transaccionales. */
    private final ProcessingService service;

    /**
     * Crea un recurso y devuelve una Location consultable.
     *
     * @param jwt        identidad
     * @param incidentId padre
     * @param request    datos estrictos
     * @return 201 tras confirmar la transacción
     */
    @PostMapping("/reviews")
    public ResponseEntity<ReviewDTO> createReviewDTO(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID incidentId,
            @RequestBody ReviewRequest request) {
        var result = service.review(subject(jwt), version(jwt), incidentId, request);
        return ResponseEntity.created(URI.create("/api/v1/incidents/" + incidentId + "/reviews/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    /**
     * Consulta una colección autorizada en SQL.
     *
     * @param jwt        identidad
     * @param incidentId padre
     * @param page       página desde uno
     * @param pageSize   máximo cien
     * @param parameters solo paginación
     * @return página privada con totales filtrados
     */
    @GetMapping("/reviews")
    public ResponseEntity<ReviewPageDTO> reviews(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID incidentId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam Map<String, String> parameters) {
        if (!Set.of("page", "pageSize").containsAll(parameters.keySet())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Parámetro no admitido.");
        }
        return ok(service.reviews(subject(jwt), version(jwt), incidentId, page, pageSize));
    }

    /**
     * Consulta la pareja exacta, ocultando recursos de otra incidencia.
     *
     * @param jwt        identidad
     * @param incidentId padre
     * @param resourceId recurso anidado
     * @return detalle autorizado
     */
    @GetMapping("/reviews/{resourceId}")
    public ResponseEntity<ReviewDTO> review(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID incidentId,
            @PathVariable UUID resourceId) {
        return ok(service.review(subject(jwt), version(jwt), incidentId, resourceId));
    }

    /**
     * Crea un recurso y devuelve una Location consultable.
     *
     * @param jwt        identidad
     * @param incidentId padre
     * @param request    datos estrictos
     * @return 201 tras confirmar la transacción
     */
    @PostMapping("/priority-decisions")
    public ResponseEntity<PriorityDecisionDTO> createPriorityDecisionDTO(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID incidentId, @RequestBody PriorityRequest request) {
        var result = service.prioritize(subject(jwt), version(jwt), incidentId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/incidents/" + incidentId + "/priority-decisions/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    /**
     * Consulta una colección autorizada en SQL.
     *
     * @param jwt        identidad
     * @param incidentId padre
     * @param page       página desde uno
     * @param pageSize   máximo cien
     * @param parameters solo paginación
     * @return página privada con totales filtrados
     */
    @GetMapping("/priority-decisions")
    public ResponseEntity<PriorityDecisionPageDTO> priorities(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID incidentId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam Map<String, String> parameters) {
        if (!Set.of("page", "pageSize").containsAll(parameters.keySet())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Parámetro no admitido.");
        }
        return ok(service.priorities(subject(jwt), version(jwt), incidentId, page, pageSize));
    }

    /**
     * Consulta la pareja exacta, ocultando recursos de otra incidencia.
     *
     * @param jwt        identidad
     * @param incidentId padre
     * @param resourceId recurso anidado
     * @return detalle autorizado
     */
    @GetMapping("/priority-decisions/{resourceId}")
    public ResponseEntity<PriorityDecisionDTO> priority(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID incidentId,
            @PathVariable UUID resourceId) {
        return ok(service.priority(subject(jwt), version(jwt), incidentId, resourceId));
    }

    /**
     * Crea un recurso y devuelve una Location consultable.
     *
     * @param jwt        identidad
     * @param incidentId padre
     * @param request    datos estrictos
     * @return 201 tras confirmar la transacción
     */
    @PostMapping("/information-responses")
    public ResponseEntity<InformationResponseDTO> createInformationResponseDTO(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID incidentId, @RequestBody InformationResponseRequest request) {
        var result = service.respond(subject(jwt), version(jwt), incidentId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/incidents/" + incidentId + "/information-responses/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    /**
     * Consulta una colección autorizada en SQL.
     *
     * @param jwt        identidad
     * @param incidentId padre
     * @param page       página desde uno
     * @param pageSize   máximo cien
     * @param parameters solo paginación
     * @return página privada con totales filtrados
     */
    @GetMapping("/information-responses")
    public ResponseEntity<InformationResponsePageDTO> responses(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID incidentId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam Map<String, String> parameters) {
        if (!Set.of("page", "pageSize").containsAll(parameters.keySet())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Parámetro no admitido.");
        }
        return ok(service.responses(subject(jwt), version(jwt), incidentId, page, pageSize));
    }

    /**
     * Consulta la pareja exacta, ocultando recursos de otra incidencia.
     *
     * @param jwt        identidad
     * @param incidentId padre
     * @param resourceId recurso anidado
     * @return detalle autorizado
     */
    @GetMapping("/information-responses/{resourceId}")
    public ResponseEntity<InformationResponseDTO> response(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID incidentId, @PathVariable UUID resourceId) {
        return ok(service.response(subject(jwt), version(jwt), incidentId, resourceId));
    }

    /**
     * Consulta el resultado ciudadano sin identificar al personal.
     *
     * @param jwt        identidad
     * @param incidentId padre
     * @return 200 con latestReview:null antes de cualquier revisión
     */
    @GetMapping("/review-outcome")
    public ResponseEntity<ReviewOutcomeDTO> outcome(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID incidentId) {
        return ok(service.outcome(subject(jwt), version(jwt), incidentId));
    }

    /**
     * Devuelve identidad desde el conversor compartido.
     *
     * @param jwt token
     * @return identidad desde el conversor compartido
     */
    private static UUID subject(Jwt jwt) {
        return DatabaseJwtAuthenticationConverter.subject(jwt);
    }

    /**
     * Devuelve versión de seguridad.
     *
     * @param jwt token
     * @return versión de seguridad
     */
    private static long version(Jwt jwt) {
        return DatabaseJwtAuthenticationConverter.version(jwt);
    }

    /**
     * Devuelve respuesta privada.
     *
     * @param body DTO autorizado
     * @param <T>  tipo de DTO
     * @return respuesta privada
     */
    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
