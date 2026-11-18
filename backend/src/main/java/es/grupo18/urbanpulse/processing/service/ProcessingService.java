package es.grupo18.urbanpulse.processing.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.grupo18.urbanpulse.audit.model.AuditEvent;
import es.grupo18.urbanpulse.audit.repository.AuditEventRepository;
import es.grupo18.urbanpulse.categories.model.Category;
import es.grupo18.urbanpulse.categories.repository.CategoryRepository;
import es.grupo18.urbanpulse.identity.dto.UserSummaryDTO;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.incidents.model.Incident;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.incidents.repository.IncidentRepository;
import es.grupo18.urbanpulse.incidents.service.IncidentAccess;
import es.grupo18.urbanpulse.processing.dto.InformationResponseDTO;
import es.grupo18.urbanpulse.processing.dto.InformationResponsePageDTO;
import es.grupo18.urbanpulse.processing.dto.InformationResponseRequest;
import es.grupo18.urbanpulse.processing.dto.PriorityDecisionDTO;
import es.grupo18.urbanpulse.processing.dto.PriorityDecisionPageDTO;
import es.grupo18.urbanpulse.processing.dto.PriorityRequest;
import es.grupo18.urbanpulse.processing.dto.PublicReviewDTO;
import es.grupo18.urbanpulse.processing.dto.ReviewDTO;
import es.grupo18.urbanpulse.processing.dto.ReviewOutcomeDTO;
import es.grupo18.urbanpulse.processing.dto.ReviewPageDTO;
import es.grupo18.urbanpulse.processing.dto.ReviewRequest;
import es.grupo18.urbanpulse.processing.model.IncidentInformationResponse;
import es.grupo18.urbanpulse.processing.model.IncidentReview;
import es.grupo18.urbanpulse.processing.model.PriorityDecision;
import es.grupo18.urbanpulse.processing.model.PrioritySource;
import es.grupo18.urbanpulse.processing.model.ReviewDecision;
import es.grupo18.urbanpulse.processing.model.StatusChange;
import es.grupo18.urbanpulse.processing.repository.IncidentInformationResponseRepository;
import es.grupo18.urbanpulse.processing.repository.IncidentReviewRepository;
import es.grupo18.urbanpulse.processing.repository.PriorityDecisionRepository;
import es.grupo18.urbanpulse.processing.repository.StatusChangeRepository;
import es.grupo18.urbanpulse.shared.error.ApiException;
import es.grupo18.urbanpulse.shared.pagination.PageMetadata;

/**
 * Casos de uso transaccionales: usuario → incidencia → categoría; nunca llamadas externas.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcessingService {

    /** Autorización común y bloqueos coordinados con Evidencias. */
    private final IncidentAccess access;

    /** Agregado versionado. */
    private final IncidentRepository incidents;

    /** Historial de revisiones. */
    private final IncidentReviewRepository reviews;

    /** Historial de prioridades. */
    private final PriorityDecisionRepository priorities;

    /** Respuestas a solicitudes exactas. */
    private final IncidentInformationResponseRepository responses;

    /** Transiciones efectivas, sin alta inicial. */
    private final StatusChangeRepository changes;

    /** Catálogo con bloqueo de desactivación. */
    private final CategoryRepository categories;

    /** Auditoría atómica con actor real. */
    private final AuditEventRepository audit;

    /** Reloj inyectado. */
    private final Clock clock;

    /**
     * Revisa una incidencia ajena y confirma categoría en VALIDATE.
     *
     * @param actor        sujeto JWT
     * @param tokenVersion versión JWT
     * @param id           incidencia
     * @param request      variante estricta
     * @return decisión confirmada al salir del proxy transaccional
     * @throws ApiException 401/403/404 por acceso, 409 por versión/estado, 422 por datos
     */
    @Transactional
    public ReviewDTO review(UUID actor, long tokenVersion, UUID id, ReviewRequest request) {
        User user = access.account(actor, tokenVersion, true);
        Incident incident = locked(user, id);
        staff(user);
        if (user.getId().equals(incident.getReporter().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SELF_REVIEW_FORBIDDEN",
                    "No puedes revisar tu propia incidencia.");
        }
        request.validate();
        expected(incident, request.expectedIncidentVersion());
        IncidentStatus from = incident.getStatus();
        if (from != IncidentStatus.REPORTED
                && !(from == IncidentStatus.AWAITING_INFORMATION && request.decision() == ReviewDecision.REJECT)) {
            throw conflict("INVALID_INCIDENT_STATE");
        }
        Category category = null;
        if (request.decision() == ReviewDecision.VALIDATE) {
            category = categories.findForUpdate(request.categoryId()).orElseThrow(ProcessingService::invalidCategory);
            if (!category.isActive())
                throw invalidCategory();
        }
        Instant at = now(incident);
        var review = new IncidentReview(incident, user, request.decision(), request.reason(), at);
        UUID previousCategory = incident.getCategory() == null ? null : incident.getCategory().getId();
        incident.transition(review.getResultingStatus(), category, at);
        reviews.save(review);
        changes.save(new StatusChange(incident, from, incident.getStatus(), user, review.getReason(), null, at));
        flush();
        Map<String, String> details = new HashMap<>();
        details.put("reviewId", review.getId().toString());
        details.put("decision", review.getDecision().name());
        if (review.getReason() != null)
            details.put("reason", review.getReason());
        if (previousCategory != null)
            details.put("previousCategoryId", previousCategory.toString());
        if (category != null)
            details.put("categoryId", category.getId().toString());
        record(user, incident, "INCIDENT_REVIEWED", details, at);
        return dto(review);
    }

    /**
     * Consume la última solicitud pendiente, sin editar campos ni fotografías.
     *
     * @param actor        informante JWT
     * @param tokenVersion versión JWT
     * @param id           incidencia
     * @param request      mensaje y versión del agregado
     * @return respuesta única y persistida
     * @throws ApiException acceso inválido, 409 por solicitud/versión o 422 por mensaje
     */
    @Transactional
    public InformationResponseDTO respond(UUID actor, long tokenVersion, UUID id, InformationResponseRequest request) {
        User user = access.account(actor, tokenVersion, true);
        Incident incident = locked(user, id);
        if (!user.getId().equals(incident.getReporter().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REPORTER_REQUIRED", "Solo el informante puede responder.");
        }
        expected(incident, request.expectedIncidentVersion());
        if (incident.getStatus() != IncidentStatus.AWAITING_INFORMATION)
            throw conflict("NO_PENDING_INFORMATION");
        IncidentReview pending = reviews.findFirstByIncident_IdOrderByReviewedAtDescIdDesc(id)
                .orElseThrow(() -> conflict("NO_PENDING_INFORMATION"));
        if (pending.getDecision() != ReviewDecision.REQUEST_INFORMATION
                || responses.existsByReview_Id(pending.getId())) {
            throw conflict("NO_PENDING_INFORMATION");
        }
        Instant at = now(incident);
        var response = new IncidentInformationResponse(pending, user, request.message(), at);
        responses.save(response);
        incident.transition(IncidentStatus.REPORTED, null, at);
        changes.save(new StatusChange(incident, IncidentStatus.AWAITING_INFORMATION, IncidentStatus.REPORTED, user,
                null, null, at));
        flush();
        record(user, incident, "INCIDENT_INFORMATION_RESPONDED",
                Map.of("reviewId", pending.getId().toString(), "responseId", response.getId().toString()), at);
        return dto(response);
    }

    /**
     * Registra un cambio manual de nivel, nunca una decisión repetida.
     *
     * @param actor        sujeto JWT
     * @param tokenVersion versión JWT
     * @param id           incidencia validada sin derivación
     * @param request      nivel, justificación y versión
     * @return decisión interna
     * @throws ApiException acceso inválido, 409 por estado/versión/nivel o 422 por texto
     */
    @Transactional
    public PriorityDecisionDTO prioritize(UUID actor, long tokenVersion, UUID id, PriorityRequest request) {
        User user = access.account(actor, tokenVersion, true);
        Incident incident = locked(user, id);
        staff(user);
        expected(incident, request.expectedIncidentVersion());
        if (request.priority() == null)
            throw invalid("La prioridad es obligatoria.");
        Instant at = now(incident);
        var decision = new PriorityDecision(incident, user, request.priority(), PrioritySource.MANUAL,
                request.justification(), at);
        incident.prioritize(request.priority(), at);
        priorities.save(decision);
        flush();
        record(user, incident, "INCIDENT_PRIORITY_CHANGED",
                Map.of("decisionId", decision.getId().toString(), "priority", decision.getPriority().name()), at);
        return dto(decision);
    }

    /**
     * Devuelve revisiones internas ordenadas y paginadas en SQL.
     *
     * @param actor        sujeto
     * @param tokenVersion JWT
     * @param id           padre
     * @param page         página
     * @param size         tamaño
     * @return revisiones internas ordenadas y paginadas en SQL
     */
    public ReviewPageDTO reviews(UUID actor, long tokenVersion, UUID id, int page, int size) {
        authorize(actor, tokenVersion, id, true);
        var result = reviews.findByIncident_Id(id, page(page, size, "reviewedAt"));
        return new ReviewPageDTO(result.map(ProcessingService::dto).getContent(), metadata(result));
    }

    /**
     * Devuelve revisión interna de ese padre.
     *
     * @param actor        sujeto
     * @param tokenVersion JWT
     * @param id           padre
     * @param child        revisión
     * @return revisión interna de ese padre
     * @throws ApiException 404 si la pareja no existe
     */
    public ReviewDTO review(UUID actor, long tokenVersion, UUID id, UUID child) {
        authorize(actor, tokenVersion, id, true);
        return dto(reviews.findByIdAndIncident_Id(child, id).orElseThrow(ProcessingService::notFound));
    }

    /**
     * Devuelve decisiones internas paginadas en SQL.
     *
     * @param actor        sujeto
     * @param tokenVersion JWT
     * @param id           padre
     * @param page         página
     * @param size         tamaño
     * @return decisiones internas paginadas en SQL
     */
    public PriorityDecisionPageDTO priorities(UUID actor, long tokenVersion, UUID id, int page, int size) {
        authorize(actor, tokenVersion, id, true);
        var result = priorities.findByIncident_Id(id, page(page, size, "decidedAt"));
        return new PriorityDecisionPageDTO(result.map(ProcessingService::dto).getContent(), metadata(result));
    }

    /**
     * Devuelve decisión interna de ese padre.
     *
     * @param actor        sujeto
     * @param tokenVersion JWT
     * @param id           padre
     * @param child        decisión
     * @return decisión interna de ese padre
     * @throws ApiException 404 si la pareja no existe
     */
    public PriorityDecisionDTO priority(UUID actor, long tokenVersion, UUID id, UUID child) {
        authorize(actor, tokenVersion, id, true);
        return dto(priorities.findByIdAndIncident_Id(child, id).orElseThrow(ProcessingService::notFound));
    }

    /**
     * Devuelve respuestas autorizadas paginadas en SQL.
     *
     * @param actor        sujeto
     * @param tokenVersion JWT
     * @param id           padre
     * @param page         página
     * @param size         tamaño
     * @return respuestas autorizadas paginadas en SQL
     */
    public InformationResponsePageDTO responses(UUID actor, long tokenVersion, UUID id, int page, int size) {
        authorize(actor, tokenVersion, id, false);
        var result = responses.findByIncident_Id(id, page(page, size, "respondedAt"));
        return new InformationResponsePageDTO(result.map(ProcessingService::dto).getContent(), metadata(result));
    }

    /**
     * Devuelve respuesta de ese padre.
     *
     * @param actor        sujeto
     * @param tokenVersion JWT
     * @param id           padre
     * @param child        respuesta
     * @return respuesta de ese padre
     * @throws ApiException 404 si la pareja no existe
     */
    public InformationResponseDTO response(UUID actor, long tokenVersion, UUID id, UUID child) {
        authorize(actor, tokenVersion, id, false);
        return dto(responses.findByIdAndIncident_Id(child, id).orElseThrow(ProcessingService::notFound));
    }

    /**
     * Devuelve resultado público sin identidad del personal ni motivos históricos de VALIDATE.
     *
     * @param actor        sujeto
     * @param tokenVersion JWT
     * @param id           padre
     * @return resultado público sin identidad del personal ni motivos históricos de VALIDATE
     */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ReviewOutcomeDTO outcome(UUID actor, long tokenVersion, UUID id) {
        Incident incident = authorize(actor, tokenVersion, id, false);
        PublicReviewDTO latest = reviews.findFirstByIncident_IdOrderByReviewedAtDescIdDesc(id)
                .map(r -> new PublicReviewDTO(r.getDecision(), r.getReviewedAt(), r.getResultingStatus(),
                        r.getDecision() == ReviewDecision.VALIDATE ? null : r.getReason(), "Ayuntamiento"))
                .orElse(null);
        return new ReviewOutcomeDTO(id, incident.getStatus(),
                incident.getStatus() == IncidentStatus.AWAITING_INFORMATION, latest);
    }

    /**
     * Devuelve incidencia visible con cuenta revalidada.
     *
     * @param actor    sujeto
     * @param version  JWT
     * @param id       padre
     * @param internal requiere personal
     * @return incidencia visible con cuenta revalidada
     */
    private Incident authorize(UUID actor, long version, UUID id, boolean internal) {
        User user = access.account(actor, version, false);
        Incident incident = access.visible(user, id, false);
        if (internal)
            staff(user);
        return incident;
    }

    /**
     * Bloquea el agregado y traduce también un snapshot JPA precargado por un llamador.
     *
     * @param user cuenta bloqueada y revalidada
     * @param id   incidencia solicitada
     * @return agregado visible bloqueado
     * @throws ApiException 409 si otra escritura invalidó la versión JPA
     */
    private Incident locked(User user, UUID id) {
        try {
            return access.visible(user, id, true);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException exception) {
            throw conflict("PROCESSING_VERSION_MISMATCH");
        }
    }

    /**
     * Fuerza todos los registros pendientes antes de construir la respuesta.
     *
     * @throws ApiException 409 por carrera optimista sobre el agregado
     */
    private void flush() {
        try {
            incidents.flush();
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException exception) {
            throw conflict("PROCESSING_VERSION_MISMATCH");
        }
    }

    /**
     * @param user cuenta vigente
     * @throws ApiException 403 sin permiso global
     */
    private static void staff(User user) {
        if (!IncidentAccess.isStaff(user))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Permisos insuficientes.");
    }

    /**
     * @param incident agregado bloqueado
     * @param expected versión enviada
     * @throws ApiException 422 si falta/no válida, 409 si obsoleta
     */
    private static void expected(Incident incident, Long expected) {
        if (expected == null || expected < 0)
            throw invalid("expectedIncidentVersion debe ser un entero no negativo.");
        if (expected != incident.getVersion())
            throw conflict("PROCESSING_VERSION_MISMATCH");
    }

    /**
     * @param user     actor real
     * @param incident agregado
     * @param action   hecho único
     * @param details  datos internos
     * @param at       instante común de la operación
     */
    private void record(User user, Incident incident, String action, Map<String, String> details, Instant at) {
        var values = new HashMap<>(details);
        values.put("version", Long.toString(incident.getVersion()));
        values.put("status", incident.getStatus().name());
        audit.saveAndFlush(new AuditEvent(user, action, "INCIDENT", incident.getId().toString(), values, at));
    }

    /**
     * Devuelve instante ordenado incluso si el reloj no avanza.
     *
     * @param incident agregado bloqueado
     * @return instante ordenado incluso si el reloj no avanza
     */
    private Instant now(Incident incident) {
        Instant current = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return current.isAfter(incident.getUpdatedAt()) ? current : incident.getUpdatedAt().plus(1, ChronoUnit.MICROS);
    }

    /**
     * Devuelve paginación SQL.
     *
     * @param p    página
     * @param s    tamaño
     * @param date campo temporal cerrado
     * @return paginación SQL
     */
    private static PageRequest page(int p, int s, String date) {
        PageMetadata.validate(p, s);
        return PageRequest.of(p - 1, s, Sort.by(Sort.Direction.DESC, date, "id"));
    }

    /**
     * Devuelve metadatos compartidos.
     *
     * @param p página consultada
     * @return metadatos compartidos
     */
    private static PageMetadata metadata(Page<?> p) {
        return new PageMetadata(p.getNumber() + 1, p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    /**
     * Devuelve DTO interno de revisión.
     *
     * @param r entidad autorizada
     * @return DTO interno de revisión
     */
    private static ReviewDTO dto(IncidentReview r) {
        return new ReviewDTO(r.getId(), r.getIncident().getId(), r.getDecision(), r.getReason(),
                UserSummaryDTO.from(r.getReviewedBy()), r.getReviewedAt(), r.getResultingStatus());
    }

    /**
     * Devuelve DTO interno de prioridad.
     *
     * @param d entidad autorizada
     * @return DTO interno de prioridad
     */
    private static PriorityDecisionDTO dto(PriorityDecision d) {
        return new PriorityDecisionDTO(d.getId(), d.getIncident().getId(), d.getPriority(), d.getSource(),
                d.getJustification(), UserSummaryDTO.from(d.getDecidedBy()), d.getDecidedAt());
    }

    /**
     * Devuelve respuesta sin identidad de personal.
     *
     * @param r entidad autorizada
     * @return respuesta sin identidad de personal
     */
    private static InformationResponseDTO dto(IncidentInformationResponse r) {
        return new InformationResponseDTO(r.getId(), r.getIncident().getId(), r.getReview().getId(), r.getMessage(),
                UserSummaryDTO.from(r.getRespondedBy()), r.getRespondedAt());
    }

    /**
     * Devuelve conflicto sin datos privados.
     *
     * @param code código estable
     * @return conflicto sin datos privados
     */
    private static ApiException conflict(String code) {
        return new ApiException(HttpStatus.CONFLICT, code,
                "La operación no es compatible con el estado o versión actual.");
    }

    /**
     * Devuelve error indistinguible de recurso anidado.
     *
     * @return error indistinguible de recurso anidado
     */
    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROCESSING_RESOURCE_NOT_FOUND", "Recurso no encontrado.");
    }

    /**
     * Devuelve error semántico de catálogo.
     *
     * @return error semántico de catálogo
     */
    private static ApiException invalidCategory() {
        return invalid("La categoría no existe o está inactiva.");
    }

    /**
     * Devuelve error semántico.
     *
     * @param message texto público
     * @return error semántico
     */
    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", message);
    }
}
