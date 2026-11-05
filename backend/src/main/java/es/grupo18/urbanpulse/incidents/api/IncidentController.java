package es.grupo18.urbanpulse.incidents.api;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.grupo18.urbanpulse.incidents.dto.CreateIncidentRequest;
import es.grupo18.urbanpulse.incidents.dto.HistoryPageDTO;
import es.grupo18.urbanpulse.incidents.dto.IncidentDTO;
import es.grupo18.urbanpulse.incidents.dto.IncidentPageDTO;
import es.grupo18.urbanpulse.incidents.dto.UpdateIncidentRequest;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.incidents.service.IncidentService;
import es.grupo18.urbanpulse.shared.error.ApiException;
import es.grupo18.urbanpulse.shared.security.DatabaseJwtAuthenticationConverter;

/** Seis operaciones de incidencias; sin rutas de borrado ni tramitación. */
@RestController
@RequestMapping("/api/v1/incidents")
@PreAuthorize("hasAuthority('EMAIL_VERIFIED')")
@RequiredArgsConstructor
public class IncidentController {
    private final IncidentService service;
    private final JsonMapper mapper;

    /**
     * Registra un aviso propio y devuelve su URI y el validador de su representación.
     * La identidad procede del JWT; el servicio revalida actividad y verificación.
     *
     * @throws ApiException 401/403 por cuenta no habilitada; 422 por datos inválidos
     * @param jwt     identidad validada
     * @param request datos de alta
     * @return 201, Location y ETag
     */
    @PostMapping
    public ResponseEntity<IncidentDTO> create(@AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateIncidentRequest request) {
        IncidentDTO result = service.create(subject(jwt), version(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/incidents/" + result.id()))
                .cacheControl(CacheControl.noStore())
                .eTag(IncidentETags.format(mapper, result)).body(result);
    }

    /**
     * Consulta administrativa con filtros inclusivos, sin aceptar filtros futuros.
     *
     * @throws ApiException 400 por paginación/filtros inválidos o 403 sin rol global
     * @param jwt         identidad validada con rol global
     * @param page        primera página 1
     * @param pageSize    tamaño por defecto 20
     * @param status      filtro opcional
     * @param categoryId  filtro opcional
     * @param createdFrom inicio inclusivo UTC
     * @param createdTo   fin inclusivo UTC
     * @param parameters  claves para rechazar filtros aún no implementados
     * @return página administrativa sin caché
     */
    @GetMapping
    @PreAuthorize("hasAuthority('EMAIL_VERIFIED') and hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<IncidentPageDTO> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam Map<String, String> parameters) {
        allowed(parameters, Set.of("page", "pageSize", "status", "categoryId", "createdFrom", "createdTo"));
        return ok(service.list(subject(jwt), version(jwt), page, pageSize, status, categoryId, createdFrom, createdTo));
    }

    /**
     * Página SQL del titular, incluso cuando también tiene permisos municipales.
     *
     * @throws ApiException 400 por paginación o parámetros no admitidos
     * @param jwt        titular
     * @param page       página
     * @param pageSize   tamaño
     * @param parameters solo paginación
     * @return página propia
     */
    @GetMapping("/mine")
    public ResponseEntity<IncidentPageDTO> mine(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam Map<String, String> parameters) {
        allowed(parameters, Set.of("page", "pageSize"));
        return ok(service.mine(subject(jwt), version(jwt), page, pageSize));
    }

    /**
     * Devuelve el detalle actual, incluidos los datos vigentes de sus relaciones.
     *
     * @throws ApiException 404 indistinguible para un recurso ajeno o inexistente
     * @param jwt        solicitante
     * @param incidentId recurso
     * @return detalle autorizado y ETag actual
     */
    @GetMapping("/{incidentId}")
    public ResponseEntity<IncidentDTO> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID incidentId) {
        IncidentDTO result = service.get(subject(jwt), version(jwt), incidentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(IncidentETags.format(mapper, result))
                .body(result);
    }

    /**
     * Corrige REPORTED bajo una ETag fuerte obtenida del servidor. La presencia de
     * categoría se conserva en el DTO; null desclasifica y omitirla no la cambia.
     *
     * @throws ApiException 400 por If-Match malformado; 404 sin acceso; 428 sin
     * precondición; 412 si cambió el detalle o hubo carrera; 409 por estado; 422 por datos
     * @param jwt        solicitante
     * @param incidentId recurso
     * @param headers    If-Match obligatorio
     * @param request    correcciones
     * @return detalle y ETag resultante
     */
    @PatchMapping("/{incidentId}")
    public ResponseEntity<IncidentDTO> patch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID incidentId,
            @RequestHeader HttpHeaders headers,
            @RequestBody UpdateIncidentRequest request) {
        String expected = IncidentETags.parse(headers.get(HttpHeaders.IF_MATCH));
        IncidentDTO result = service.patch(subject(jwt), version(jwt), incidentId, expected, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(IncidentETags.format(mapper, result))
                .body(result);
    }

    /**
     * Consulta eventos públicos del recurso autorizado, con orden temporal ascendente.
     *
     * @throws ApiException 404 sin acceso; 400 por paginación o parámetros inválidos
     * @param jwt        solicitante
     * @param incidentId recurso
     * @param page       página
     * @param pageSize   tamaño
     * @param parameters solo paginación
     * @return historial público
     */
    @GetMapping("/{incidentId}/history")
    public ResponseEntity<HistoryPageDTO> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID incidentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam Map<String, String> parameters) {
        allowed(parameters, Set.of("page", "pageSize"));
        return ok(service.history(subject(jwt), version(jwt), incidentId, page, pageSize));
    }

    /** @return sujeto desde el conversor JWT compartido */
    private static UUID subject(Jwt jwt) {
        return DatabaseJwtAuthenticationConverter.subject(jwt);
    }

    /** @return versión de seguridad, distinta de Incident.version */
    private static long version(Jwt jwt) {
        return DatabaseJwtAuthenticationConverter.version(jwt);
    }

    /** @return respuesta privada que no debe almacenarse */
    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /**
     * Rechaza filtros futuros y userId arbitrario en vez de aparentar que se
     * aplicaron.
     *
     * @param parameters parámetros recibidos
     * @param allowed nombres admitidos por esta ruta
     * @throws ApiException 400 si hay una clave no admitida
     */
    private static void allowed(Map<String, String> parameters, Set<String> allowed) {
        if (!allowed.containsAll(parameters.keySet())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Parámetro no admitido.");
        }
    }
}
