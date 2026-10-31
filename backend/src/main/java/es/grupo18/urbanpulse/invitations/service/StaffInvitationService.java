package es.grupo18.urbanpulse.invitations.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Validator;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.grupo18.urbanpulse.audit.model.AuditEvent;
import es.grupo18.urbanpulse.audit.repository.AuditEventRepository;
import es.grupo18.urbanpulse.identity.model.Role;
import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.identity.model.UserRole;
import es.grupo18.urbanpulse.identity.model.UserRoleId;
import es.grupo18.urbanpulse.identity.repository.RoleRepository;
import es.grupo18.urbanpulse.identity.repository.UserRepository;
import es.grupo18.urbanpulse.identity.repository.UserRoleRepository;
import es.grupo18.urbanpulse.invitations.config.StaffInvitationProperties;
import es.grupo18.urbanpulse.invitations.dto.InvitationDTO;
import es.grupo18.urbanpulse.invitations.dto.InvitationPageDTO;
import es.grupo18.urbanpulse.invitations.dto.InvitationQuery;
import es.grupo18.urbanpulse.invitations.dto.InvitationRedemptionDTO;
import es.grupo18.urbanpulse.invitations.dto.InvitationRequest;
import es.grupo18.urbanpulse.invitations.mail.StaffInvitationRequested;
import es.grupo18.urbanpulse.invitations.model.InvitationStatus;
import es.grupo18.urbanpulse.invitations.model.StaffInvitation;
import es.grupo18.urbanpulse.invitations.repository.StaffInvitationRepository;
import es.grupo18.urbanpulse.organization.model.Department;
import es.grupo18.urbanpulse.organization.model.DepartmentMembership;
import es.grupo18.urbanpulse.organization.repository.DepartmentMembershipRepository;
import es.grupo18.urbanpulse.organization.repository.DepartmentRepository;
import es.grupo18.urbanpulse.shared.error.ApiException;
import es.grupo18.urbanpulse.shared.pagination.PageMetadata;

/**
 * Ciclo completo de invitaciones. Orden de bloqueos: usuario, invitación,
 * departamento.
 * El usuario serializa incluso códigos diferentes que conceden el mismo
 * permiso.
 * Cada operación pública entra por el proxy transaccional; no hay
 * autoinvocación.
 */
@Service
@RequiredArgsConstructor
public class StaffInvitationService {
    private final StaffInvitationRepository invitations;
    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final RoleRepository roles;
    private final DepartmentRepository departments;
    private final DepartmentMembershipRepository memberships;
    private final AuditEventRepository audit;
    private final InvitationCodeService codes;
    private final StaffInvitationProperties properties;
    private final ApplicationEventPublisher events;
    private final Validator validator;
    private final EntityManager entityManager;
    private final Clock clock;

    /**
     * Emite una invitación y su auditoría, sin crear ni buscar la cuenta
     * destinataria.
     *
     * @param actor   identificador ADMIN del JWT
     * @param version versión JWT que se revalida bajo bloqueo
     * @param request variante de creación estricta
     * @return metadatos; nunca código ni digest
     * @throws ApiException por autenticación, permisos o datos inválidos
     */
    @Transactional
    public InvitationDTO create(UUID actor, long version, InvitationRequest request) {
        User admin = account(actor, version, true);
        validate(request);

        if (request.role() == RoleCode.CITIZEN || request.role().isDepartmental() != (request.departmentId() != null)) {
            throw invalidInput();
        }

        Department department = request.departmentId() == null ? null : activeDepartment(request.departmentId());
        Role role = roles.findById(request.role()).orElseThrow(StaffInvitationService::invalidInput);
        Instant now = clock.instant();

        String code = codes.generate();

        StaffInvitation invitation = invitations.saveAndFlush(new StaffInvitation(codes.hash(code), request.email(),
                role, department, admin, now, now.plus(Duration.ofDays(properties.ttlDays()))));

        recordEvent(admin, "STAFF_INVITATION_CREATED", invitation, now);

        events.publishEvent(new StaffInvitationRequested(invitation.getEmail(), code, role.getCode(),
                department == null ? null : department.getName(), invitation.getExpiresAt()));

        return InvitationDTO.from(invitation, now);
    }

    /**
     * Consulta un recurso administrativo.
     *
     * @param actor   ADMIN solicitante
     * @param version versión JWT
     * @param id      invitación
     * @return metadatos con estado derivado
     * @throws ApiException si falta autorización o el recurso no existe
     */
    @Transactional
    public InvitationDTO get(UUID actor, long version, UUID id) {
        account(actor, version, true);
        return InvitationDTO.from(invitations.findById(id).orElseThrow(StaffInvitationService::notFound),
                clock.instant());
    }

    /**
     * Filtra en SQL antes de paginar; usa un solo instante para estado y
     * proyección.
     *
     * @param actor   ADMIN solicitante
     * @param version versión JWT
     * @param query   filtros exactos y página basada en 1
     * @return página vacía con totales reales si el offset supera los resultados
     * @throws ApiException si faltan permisos o los filtros no son admisibles
     */
    @Transactional
    public InvitationPageDTO list(UUID actor, long version, InvitationQuery query) {
        account(actor, version, true);
        validate(query);

        if ((long) (query.page() - 1) * query.pageSize() > Integer.MAX_VALUE
                || (query.email() != null && query.email().isBlank())) {
            throw invalidInput();
        }

        Instant now = clock.instant();

        var page = invitations.findAll(filters(query, now), PageRequest.of(query.page() - 1, query.pageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));

        return new InvitationPageDTO(page.getContent().stream().map(i -> InvitationDTO.from(i, now)).toList(),
                new PageMetadata(query.page(), query.pageSize(), page.getTotalElements(), page.getTotalPages()));
    }

    /**
     * Revoca exclusivamente una invitación ACTIVE, conservando actor e historia.
     *
     * @param actor   ADMIN responsable
     * @param version versión JWT
     * @param id      invitación
     * @throws ApiException por falta de permisos, ausencia o estado final (409)
     */
    @Transactional
    public void revoke(UUID actor, long version, UUID id) {
        User admin = account(actor, version, true);
        StaffInvitation invitation = invitations.findLockedById(id).orElseThrow(StaffInvitationService::notFound);
        Instant now = clock.instant();

        if (invitation.statusAt(now) != InvitationStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "INVITATION_NOT_ACTIVE", "La invitación no está activa.");
        }

        invitation.revoke(admin, now);
        recordEvent(admin, "STAFF_INVITATION_REVOKED", invitation, now);
        invitations.flush();
    }

    /**
     * Canjea una invitación activa de un solo uso para el titular autenticado.
     * Comprueba la identidad, el destinatario, el estado de la invitación y
     * la ausencia de un permiso equivalente antes de concederlo y auditarlo.
     *
     * @param actor   identificador del usuario autenticado
     * @param version versión del JWT presentada por el usuario
     * @param code    código de invitación recibido en el cuerpo de la petición
     * @return datos del permiso concedido y el instante del canje
     * @throws ApiException si la cuenta o su versión no son válidas (401),
     *                      falta verificar el correo (403), la invitación
     *                      o el departamento no son válidos (422), o el
     *                      usuario ya posee el permiso (409)
     */
    @Transactional
    public InvitationRedemptionDTO redeem(UUID actor, long version, String code) {
        if (!codes.validFormat(code)) {
            throw invalidCode();
        }

        User user = account(actor, version, false);

        StaffInvitation invitation = invitations.findLockedByCodeHash(codes.hash(code))
                .orElseThrow(StaffInvitationService::invalidCode);

        if (invitation.statusAt(clock.instant()) != InvitationStatus.ACTIVE
                || !user.getEmail().equals(invitation.getEmail())) {
            throw invalidCode();
        }

        Role role = invitation.getRole();

        Department department = invitation.getDepartment() == null ? null
                : activeDepartment(invitation.getDepartment().getId());

        Instant now = clock.instant();

        if (invitation.statusAt(now) != InvitationStatus.ACTIVE) {
            throw invalidCode();
        }

        grant(user, role, department, now);
        invitation.redeem(user, now);
        recordEvent(user, "STAFF_INVITATION_REDEEMED", invitation, now);
        invitations.flush();

        return new InvitationRedemptionDTO(invitation.getId(), role.getCode(),
                role.getCode().isGlobal() ? "GLOBAL" : "DEPARTMENT", department == null ? null : department.getId(),
                now);
    }

    /**
     * Bloquea la cuenta y vuelve a comprobar su estado, versión de token
     * y roles actuales en la base de datos. Exige correo verificado y,
     * cuando se solicita, rol global ADMIN.
     *
     * @param id            identificador del usuario autenticado
     * @param version       versión presentada por el JWT previamente validado
     * @param adminRequired indica si debe exigirse el rol ADMIN
     * @return cuenta bloqueada y autorizada para continuar
     * @throws ApiException si la cuenta o versión no son válidas (401),
     *                      o falta verificación o autorización (403)
     */
    private User account(UUID id, long version, boolean adminRequired) {
        User user = users.findForEmailVerification(id).orElseThrow(ApiException::unauthorized);
        Set<RoleCode> currentRoles = user.globalRoles();

        if (!user.isActive() || user.getTokenVersion() != version || !currentRoles.contains(RoleCode.CITIZEN)) {
            throw ApiException.unauthorized();
        }

        if (!user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Debes verificar tu correo.");
        }

        if (adminRequired && !currentRoles.contains(RoleCode.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Permisos insuficientes.");
        }

        return user;
    }

    /**
     * Recupera y bloquea un departamento que debe existir y estar activo
     * para emitir o canjear una invitación departamental.
     *
     * @param id identificador del departamento
     * @return departamento activo recuperado bajo bloqueo
     * @throws ApiException si el departamento no existe o está inactivo (422)
     */
    private Department activeDepartment(UUID id) {
        return departments.findForInvitation(id)
                .filter(department -> department.isActive())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "INVALID_DEPARTMENT",
                        "El departamento no está disponible."));
    }

    /**
     * Concede un rol global o una pertenencia departamental si el usuario
     * todavía no posee ese permiso. Inserta y fuerza la escritura para
     * detectar también duplicados protegidos por restricciones SQL.
     *
     * @param user       titular al que se concede el permiso
     * @param role       rol concedido
     * @param department departamento del permiso, o {@code null} para rol global
     * @param now        instante de concesión de la pertenencia departamental
     * @throws ApiException si ya existe un permiso equivalente (409)
     */
    private void grant(User user, Role role, Department department, Instant now) {
        boolean present = department == null
                ? userRoles.existsById(new UserRoleId(user.getId(), role.getCode()))
                : memberships.existsByUserIdAndDepartmentIdAndRoleCodeAndLeftAtIsNull(
                        user.getId(), department.getId(), role.getCode());
        if (present) {
            throw duplicatePermission();
        }

        try {
            // persist evita que merge silencie una inserción duplicada de clave compuesta.
            entityManager.persist(department == null ? new UserRole(user, role)
                    : new DepartmentMembership(user, department, role, now));
            entityManager.flush();
        }

        catch (RuntimeException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                        && Set.of("user_roles_pkey", "department_memberships_active_unique")
                                .contains(Objects.toString(constraint.getConstraintName(), ""))) {
                    throw duplicatePermission();
                }
            }

            throw ex;
        }
    }

    /**
     * Registra un evento de auditoría de invitaciones sin incluir el código
     * original ni su HMAC. Conserva el rol y, cuando procede, el departamento.
     *
     * @param actor      usuario que realiza la operación
     * @param action     código de la acción auditada
     * @param invitation invitación sobre la que se ha actuado
     * @param now        instante de la operación
     */
    private void recordEvent(User actor, String action, StaffInvitation invitation, Instant now) {
        Map<String, String> details = new HashMap<>();
        RoleCode role = invitation.getRole().getCode();
        details.put("role", role.name());
        details.put("scope", role.isGlobal() ? "GLOBAL" : "DEPARTMENT");

        if (invitation.getDepartment() != null) {
            details.put("departmentId", invitation.getDepartment().getId().toString());
        }

        audit.saveAndFlush(new AuditEvent(actor, action, "StaffInvitation",
                invitation.getId().toString(), details, now));
    }

    /**
     * Construye los predicados SQL para filtrar las invitaciones antes de
     * paginarlas. Calcula ACTIVE y EXPIRED respecto a un mismo instante;
     * la igualdad con expiresAt corresponde a EXPIRED.
     *
     * @param filter filtros opcionales de estado, destinatario, rol y departamento
     * @param now    instante usado para determinar los estados temporales
     * @return especificación JPA aplicable a la consulta paginada
     */
    private Specification<StaffInvitation> filters(InvitationQuery filter, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.email() != null) {
                predicates.add(cb.equal(root.get("email"), filter.email()));
            }

            if (filter.role() != null) {
                predicates.add(cb.equal(root.get("role").get("code"), filter.role()));
            }

            if (filter.departmentId() != null) {
                predicates.add(cb.equal(root.get("department").get("id"), filter.departmentId()));
            }

            if (filter.status() != null) {
                Predicate pending = cb.and(cb.isNull(root.get("redeemedAt")), cb.isNull(root.get("revokedAt")));
                predicates.add(switch (filter.status()) {
                    case ACTIVE -> cb.and(pending, cb.greaterThan(root.get("expiresAt"), now));
                    case EXPIRED -> cb.and(pending, cb.lessThanOrEqualTo(root.get("expiresAt"), now));
                    case REDEEMED -> cb.isNotNull(root.get("redeemedAt"));
                    case REVOKED -> cb.isNotNull(root.get("revokedAt"));
                });
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Comprueba que la solicitud no sea nula y que cumpla las restricciones
     * de Bean Validation declaradas en su tipo.
     *
     * @param request objeto que se desea validar
     * @throws ApiException si es nulo o incumple alguna restricción (422)
     */
    private void validate(Object request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw invalidInput();
        }
    }

    /**
     * Construye el error uniforme para datos de entrada no válidos.
     *
     * @return excepción HTTP 422 con código VALIDATION_ERROR
     */
    private static ApiException invalidInput() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", "Los datos no son válidos.");
    }

    /**
     * Construye un error no enumerable para un código de invitación no válido,
     * caducado, revocado, utilizado o perteneciente a otra identidad.
     *
     * @return excepción HTTP 422 con código INVALID_INVITATION_CODE
     */
    private static ApiException invalidCode() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                "INVALID_INVITATION_CODE", "El código de invitación no es válido.");
    }

    /**
     * Construye el error para un permiso que el usuario ya posee.
     *
     * @return excepción HTTP 409 con código PERMISSION_ALREADY_GRANTED
     */
    private static ApiException duplicatePermission() {
        return new ApiException(HttpStatus.CONFLICT, "PERMISSION_ALREADY_GRANTED", "El permiso ya está concedido.");
    }

    /**
     * Construye el error para una invitación administrativa inexistente.
     *
     * @return excepción HTTP 404 con código INVITATION_NOT_FOUND
     */
    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "Invitación no encontrada.");
    }
}
