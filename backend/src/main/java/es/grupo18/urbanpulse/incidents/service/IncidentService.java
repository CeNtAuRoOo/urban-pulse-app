package es.grupo18.urbanpulse.incidents.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.grupo18.urbanpulse.audit.model.AuditEvent;
import es.grupo18.urbanpulse.audit.repository.AuditEventRepository;
import es.grupo18.urbanpulse.categories.dto.CategoryResponse;
import es.grupo18.urbanpulse.categories.model.Category;
import es.grupo18.urbanpulse.categories.repository.CategoryRepository;
import es.grupo18.urbanpulse.identity.dto.UserSummaryDTO;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.incidents.api.IncidentETags;
import es.grupo18.urbanpulse.incidents.dto.CoordinatesDTO;
import es.grupo18.urbanpulse.incidents.dto.CreateIncidentRequest;
import es.grupo18.urbanpulse.incidents.dto.HistoryEventDTO;
import es.grupo18.urbanpulse.incidents.dto.HistoryPageDTO;
import es.grupo18.urbanpulse.incidents.dto.IncidentDTO;
import es.grupo18.urbanpulse.incidents.dto.IncidentPageDTO;
import es.grupo18.urbanpulse.incidents.dto.IncidentSummaryDTO;
import es.grupo18.urbanpulse.incidents.dto.LocationDTO;
import es.grupo18.urbanpulse.incidents.dto.UpdateIncidentRequest;
import es.grupo18.urbanpulse.incidents.model.Incident;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.incidents.model.Location;
import es.grupo18.urbanpulse.incidents.repository.IncidentRepository;
import es.grupo18.urbanpulse.organization.dto.DepartmentResponse;
import es.grupo18.urbanpulse.shared.error.ApiException;
import es.grupo18.urbanpulse.shared.pagination.PageMetadata;

import tools.jackson.databind.json.JsonMapper;

/**
 * Casos de uso del núcleo; permisos, cambios y auditoría se resuelven en la
 * transacción.
 */
@Service
@RequiredArgsConstructor
public class IncidentService {

    private static final String RESOURCE = "INCIDENT";
    private static final List<String> PUBLIC_ACTIONS = List.of("INCIDENT_CREATED", "INCIDENT_UPDATED");
    private final IncidentRepository incidents;
    private final IncidentAccess access;
    private final CategoryRepository categories;
    private final AuditEventRepository audit;
    private final Clock clock;
    private final JsonMapper mapper;

    /**
     * Registra exclusivamente en nombre del actor y audita en la misma transacción.
     *
     * @param actor        sujeto obtenido del JWT validado
     * @param tokenVersion versión de seguridad del JWT
     * @param request      campos admitidos para alta
     * @return detalle con UUID y versión ya persistidos
     * @throws ApiException por identidad, verificación o datos inválidos
     */
    @Transactional
    public IncidentDTO create(UUID actor, long tokenVersion, CreateIncidentRequest request) {
        User reporter = account(actor, tokenVersion, true);
        if (request == null || request.coordinates() == null) {
            throw invalid("Las coordenadas son obligatorias.");
        }
        Incident incident = new Incident(reporter, request.title(), request.description(),
                activeCategory(request.categoryId()), new Location(request.coordinates().toValue()), clock.instant());
        incidents.saveAndFlush(incident);
        record(reporter, incident, "INCIDENT_CREATED");
        return detail(incident);
    }

    /**
     * Consulta solo tras revalidar cuenta y visibilidad.
     *
     * @param actor        sujeto autenticado
     * @param tokenVersion versión JWT
     * @param id           incidencia
     * @return detalle mínimo autorizado
     * @throws ApiException 404 indistinguible para inexistencia o falta de
     *                      titularidad
     */
    @Transactional(readOnly = true)
    public IncidentDTO get(UUID actor, long tokenVersion, UUID id) {
        return detail(visible(account(actor, tokenVersion, false), id));
    }

    /**
     * Corrige solo REPORTED con precondición obligatoria y bloqueo optimista JPA.
     * La precondición se evalúa sobre el detalle actual antes de modificarlo; un
     * cambio relacionado posterior es otra operación y tendrá otra ETag al
     * consultarlo.
     * {@code @Version} protege además las escrituras concurrentes de la propia
     * incidencia.
     * El permiso transversal de OPERATOR/ADMIN es provisional y requiere revisión
     * futura.
     *
     * @param actor        sujeto JWT
     * @param tokenVersion versión de seguridad
     * @param id           incidencia
     * @param expectedTag  ETag fuerte interpretada por HTTP; null si no existe
     * @param request      campos presentes, con null de categoría distinto de
     *                     omisión
     * @return detalle; un no-op conserva versión y no produce auditoría
     * @throws ApiException 404 sin acceso, 428 sin precondición, 412 obsoleta, 409
     *                      estado o 422 datos
     */
    @Transactional
    public IncidentDTO patch(UUID actor, long tokenVersion, UUID id, String expectedTag,
            UpdateIncidentRequest request) {
        User user = account(actor, tokenVersion, true);
        Incident incident = visible(user, id);
        if (expectedTag == null) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "IF_MATCH_REQUIRED", "Se requiere If-Match.");
        }
        if (!expectedTag.equals(IncidentETags.format(mapper, detail(incident)))) {
            throw stale();
        }
        if (incident.getStatus() != IncidentStatus.REPORTED) {
            throw new ApiException(HttpStatus.CONFLICT, "INCIDENT_NOT_REPORTED",
                    "Solo se pueden corregir reportes iniciales.");
        }
        if (request == null || !request.hasChanges()) {
            throw invalid("Debe proporcionar al menos un campo para actualizar.");
        }
        Category category = request.categoryPresent() ? activeCategory(request.categoryId()) : incident.getCategory();
        Location location = request.coordinates() == null ? incident.getLocation()
                : new Location(request.coordinates().toValue());
        boolean changed = incident.correct(request.title() == null ? incident.getTitle() : request.title(),
                request.description() == null ? incident.getDescription() : request.description(), category, location,
                clock.instant());
        if (changed) {
            try {
                incidents.flush();
            } catch (ObjectOptimisticLockingFailureException exception) {
                throw stale();
            }
            record(user, incident, "INCIDENT_UPDATED");
        }
        return detail(incident);
    }

    /**
     * Lista administrativamente mediante filtros tipados y parámetros enlazados.
     *
     * @param actor        sujeto JWT
     * @param tokenVersion versión JWT
     * @param page         página desde 1
     * @param pageSize     tamaño de 1 a 100
     * @param status       estado opcional
     * @param categoryId   categoría opcional, incluidas históricas inactivas
     * @param createdFrom  inicio inclusivo UTC
     * @param createdTo    fin inclusivo UTC
     * @return página SQL ordenada por createdAt DESC, id DESC
     * @throws ApiException 403 sin rol global OPERATOR/ADMIN; 400 para intervalo
     *                      invertido
     */
    @Transactional(readOnly = true)
    public IncidentPageDTO list(UUID actor, long tokenVersion, int page, int pageSize, IncidentStatus status,
            UUID categoryId, Instant createdFrom, Instant createdTo) {
        if (!isStaff(account(actor, tokenVersion, false))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Permisos insuficientes.");
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                    "El intervalo de creación está invertido.");
        }
        return search(null, page, pageSize, status, categoryId, createdFrom, createdTo);
    }

    /**
     * Lista desde SQL por titular; los roles departamentales no amplían el ámbito.
     *
     * @param actor        sujeto JWT, nunca un userId proporcionado en query
     * @param tokenVersion versión JWT
     * @param page         página desde 1
     * @param pageSize     tamaño máximo 100
     * @return solo incidencias propias con totales propios
     */
    @Transactional(readOnly = true)
    public IncidentPageDTO mine(UUID actor, long tokenVersion, int page, int pageSize) {
        account(actor, tokenVersion, false);
        return search(actor, page, pageSize, null, null, null, null);
    }

    /**
     * Proyecta únicamente eventos públicos del recurso exacto, paginados en SQL.
     *
     * @param actor        sujeto JWT
     * @param tokenVersion versión JWT
     * @param id           incidencia autorizada
     * @param page         página desde 1
     * @param pageSize     máximo 100
     * @return historial ocurrido ASC, UUID ASC; nunca detalles arbitrarios de
     *         auditoría
     */
    @Transactional(readOnly = true)
    public HistoryPageDTO history(UUID actor, long tokenVersion, UUID id, int page, int pageSize) {
        visible(account(actor, tokenVersion, false), id);
        PageMetadata.validate(page, pageSize);
        Page<AuditEvent> events = audit.findByResourceTypeAndResourceIdAndActionIn(RESOURCE, id.toString(),
                PUBLIC_ACTIONS,
                PageRequest.of(page - 1, pageSize, Sort.by("occurredAt", "id")));
        return new HistoryPageDTO(events.stream().map(this::historyEvent).toList(), metadata(events));
    }

    /**
     * Revalida seguridad en base de datos; las escrituras coordinan el bloqueo con
     * identidad.
     *
     * @param id           sujeto autenticado
     * @param tokenVersion versión de seguridad que debe seguir vigente
     * @param writing      true para bloquear el actor hasta terminar la escritura
     * @return cuenta con roles cargados
     * @throws ApiException 401 por cuenta inválida/inactiva o 403 sin verificar
     */
    private User account(UUID id, long tokenVersion, boolean writing) {
        return access.account(id, tokenVersion, writing);
    }

    /**
     * Carga el recurso y oculta su existencia si no pertenece al solicitante sin
     * rol global.
     *
     * @param actor cuenta previamente revalidada
     * @param id    identidad del recurso
     * @return incidencia con relaciones to-one necesarias para el detalle
     * @throws ApiException 404 si no existe o no es visible
     */
    private Incident visible(User actor, UUID id) {
        return access.visible(actor, id, false);
    }

    /** Solo UserRole otorga acceso transversal; nunca DepartmentMembership. */
    private static boolean isStaff(User user) {
        return IncidentAccess.isStaff(user);
    }

    /**
     * Bloquea el catálogo como sus escrituras para impedir aceptar una
     * desactivación concurrente.
     *
     * @param id categoría solicitada, o null para desclasificar
     * @return entidad activa bloqueada, o null
     * @throws ApiException 422 si no existe o está inactiva
     */
    private Category activeCategory(UUID id) {
        if (id == null) {
            return null;
        }
        Category category = categories.findForUpdate(id)
                .orElseThrow(() -> invalid("La categoría no existe o está inactiva."));
        if (!category.isActive()) {
            throw invalid("La categoría no existe o está inactiva.");
        }
        return category;
    }

    /**
     * Combina filtros mediante AND y pagina en SQL antes de proyectar los DTO.
     *
     * @param reporter titular obligatorio para mine; null en consulta
     *                 administrativa
     * @param page     página desde 1
     * @param pageSize tamaño entre 1 y 100
     * @param status   estado opcional
     * @param category categoría opcional, sin exigir actividad actual
     * @param from     inicio UTC inclusivo o null
     * @param to       fin UTC inclusivo o null
     * @return página con orden createdAt DESC, id DESC y totales filtrados
     * @throws ApiException 400 si la página, tamaño u offset no son válidos
     */
    private IncidentPageDTO search(UUID reporter, int page, int pageSize, IncidentStatus status, UUID category,
            Instant from, Instant to) {
        PageMetadata.validate(page, pageSize);
        Specification<Incident> filter = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (reporter != null) {
                predicates.add(builder.equal(root.get("reporter").get("id"), reporter));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (category != null) {
                predicates.add(builder.equal(root.get("category").get("id"), category));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<Incident> result = incidents.findAll(filter,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new IncidentPageDTO(result.stream().map(i -> new IncidentSummaryDTO(i.getId(), i.getTitle(),
                category(i.getCategory()), location(i), i.getStatus(), i.getPriority(), i.getCreatedAt(),
                i.getUpdatedAt(), i.getVersion())).toList(), metadata(result));
    }

    /**
     * Fuerza también el evento dentro de la transacción; cualquier fallo revierte
     * ambos cambios.
     *
     * @param actor    autor vigente
     * @param incident incidencia ya insertada o actualizada
     * @param action   acción interna CREATED/UPDATED prefijada por INCIDENT_
     */
    private void record(User actor, Incident incident, String action) {
        audit.saveAndFlush(new AuditEvent(actor, action, RESOURCE, incident.getId().toString(),
                Map.of("version", Long.toString(incident.getVersion())), incident.getUpdatedAt()));
    }

    /**
     * Construye un texto estable y una lista cerrada de detalles; nunca devuelve el
     * mapa de auditoría.
     *
     * @param event evento filtrado previamente por recurso y lista permitida de
     *              acciones
     * @return proyección pública con versión opcional y actor mínimo
     */
    private HistoryEventDTO historyEvent(AuditEvent event) {
        boolean created = "INCIDENT_CREATED".equals(event.getAction());
        String version = event.getDetails().get("version");
        Map<String, String> details = version != null && version.matches("[0-9]{1,19}") ? Map.of("version", version)
                : Map.of();
        return new HistoryEventDTO(event.getId(), created ? "CREATED" : "UPDATED", event.getOccurredAt(),
                UserSummaryDTO.from(event.getActor()), created ? "Incidencia registrada." : "Incidencia corregida.",
                details);
    }

    /** Proyecta solo datos de negocio autorizados y reutiliza DTO de catálogos. */
    private static IncidentDTO detail(Incident i) {
        var d = i.getAssignedDepartment();
        return new IncidentDTO(i.getId(), i.getTitle(), i.getDescription(), UserSummaryDTO.from(i.getReporter()),
                category(i.getCategory()), location(i), i.getStatus(), i.getPriority(),
                d == null ? null
                        : new DepartmentResponse(d.getId(), d.getName(), d.getCode(), d.getDescription(), d.isActive()),
                i.getCreatedAt(), i.getUpdatedAt(), i.getResolvedAt(), i.getClosedAt(), i.getVersion());
    }

    /** @return contrato existente de Category, conservando description:null */
    private static CategoryResponse category(Category c) {
        return c == null ? null : new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive());
    }

    /** @return localización sin enriquecimientos ficticios */
    private static LocationDTO location(Incident i) {
        return new LocationDTO(CoordinatesDTO.from(i.getLocation().getCoordinates()));
    }

    /** @return metadatos compartidos con los totales de la consulta */
    private static PageMetadata metadata(Page<?> page) {
        return new PageMetadata(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** @return respuesta indistinguible para inexistencia y acceso ajeno */
    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incidencia no encontrada.");
    }

    /**
     * @return conflicto de precondición, también para carreras detectadas al flush
     */
    private static ApiException stale() {
        return new ApiException(HttpStatus.PRECONDITION_FAILED, "INCIDENT_VERSION_MISMATCH",
                "La versión de la incidencia ha cambiado.");
    }

    /** @return error público de validación sin datos internos */
    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", message);
    }
}
