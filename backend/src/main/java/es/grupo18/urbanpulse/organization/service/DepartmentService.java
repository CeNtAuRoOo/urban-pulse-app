package es.grupo18.urbanpulse.organization.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.hibernate.exception.ConstraintViolationException;

import es.grupo18.urbanpulse.organization.dto.CreateDepartmentRequest;
import es.grupo18.urbanpulse.organization.dto.DepartmentResponse;
import es.grupo18.urbanpulse.organization.dto.PatchDepartmentRequest;
import es.grupo18.urbanpulse.organization.model.Department;
import es.grupo18.urbanpulse.organization.repository.DepartmentRepository;

/** Administración transaccional del catálogo municipal con normalización y control de concurrencia. */
@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    /**
     * Configura la persistencia compartida con Invitaciones.
     *
     * @param departmentRepository repositorio único de departamentos
     */
    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    /**
     * Consulta el catálogo completo, incluidas unidades inactivas.
     *
     * @return proyecciones ordenadas por nombre según la collation de PostgreSQL
     */
    @Transactional(readOnly = true)
    public List<DepartmentResponse> findAll() {
        return departmentRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Obtiene la proyección de una unidad por su identidad estable.
     *
     * @param id UUID de departamento
     * @return datos de negocio sin las fechas internas
     * @throws DepartmentNotFoundException si no existe
     */
    @Transactional(readOnly = true)
    public DepartmentResponse findById(UUID id) {
        return toResponse(findEntity(id));
    }

    /**
     * Normaliza nombre y código y persiste una unidad en una única transacción.
     *
     * @param request alta validada; active omitido o null equivale a true
     * @return unidad persistida con UUIDv7
     * @throws DuplicateDepartmentException si nombre o código ya están ocupados
     * @throws InvalidDepartmentException si la longitud del nombre normalizado no es válida
     */
    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        String name = normalizeName(request.name());
        String code = normalizeCode(request.code());
        assertUnique(name, code, null);

        Department department = new Department(name, code, request.description(),
                request.active() == null || request.active());
        return persist(department);
    }

    /**
     * Bloquea la unidad hasta finalizar la transacción para conservar cambios concurrentes y coordinarse con
     * Invitaciones.
     *
     * @param id UUID de departamento
     * @param request parche validado; null en la representación Java significa campo omitido
     * @return proyección actualizada; desactivar conserva historial y pertenencias
     * @throws DepartmentNotFoundException si no existe
     * @throws DuplicateDepartmentException si el nombre o código está ocupado por otra unidad
     */
    @Transactional
    public DepartmentResponse patch(UUID id, PatchDepartmentRequest request) {
        Department department = departmentRepository.findForUpdate(id)
                .orElseThrow(() -> new DepartmentNotFoundException(id));
        String name = request.name() == null ? department.getName() : normalizeName(request.name());
        String code = request.code() == null ? department.getCode() : normalizeCode(request.code());
        assertUnique(name, code, id);

        department.setName(name);
        department.setCode(code);
        if (request.description() != null) {
            department.setDescription(request.description());
        }
        if (request.active() != null) {
            department.setActive(request.active());
        }
        return persist(department);
    }

    /**
     * Fuerza el flush para detectar carreras SQL; solo traduce 23505 de los dos índices conocidos a
     * DuplicateDepartmentException.
     *
     * @param department entidad que se guarda
     * @return proyección persistida
     * @throws DuplicateDepartmentException si un índice único conocido detecta una carrera
     * @throws DataIntegrityViolationException para las demás infracciones de integridad
     */
    private DepartmentResponse persist(Department department) {
        try {
            return toResponse(departmentRepository.saveAndFlush(department));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "23505".equals(violation.getSQLState())
                        && Set.of("uk_departments_name_normalized", "uk_departments_code_normalized")
                                .contains(violation.getConstraintName() == null ? "" : violation.getConstraintName())) {
                    throw new DuplicateDepartmentException("nombre o código", "indicado");
                }
            }
            throw exception;
        }
    }

    /**
     * Resuelve una entidad sin solicitar bloqueo de escritura; lanza DepartmentNotFoundException si no existe.
     *
     * @param id UUID solicitado
     * @return entidad existente
     * @throws DepartmentNotFoundException si no existe la unidad
     */
    private Department findEntity(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new DepartmentNotFoundException(id));
    }

    /**
     * Detecta duplicados anticipadamente; los índices SQL siguen protegiendo las carreras concurrentes.
     *
     * @param name nombre normalizado
     * @param code código normalizado
     * @param id unidad excluida, o null durante el alta
     * @throws DuplicateDepartmentException si otra unidad ocupa el nombre o código
     */
    private void assertUnique(String name, String code, UUID id) {
        boolean duplicatedName = id == null
                ? departmentRepository.existsByNameIgnoreCase(name)
                : departmentRepository.existsByNameIgnoreCaseAndIdNot(name, id);
        if (duplicatedName) {
            throw new DuplicateDepartmentException("nombre", name);
        }

        boolean duplicatedCode = id == null
                ? departmentRepository.existsByCodeIgnoreCase(code)
                : departmentRepository.existsByCodeIgnoreCaseAndIdNot(code, id);
        if (duplicatedCode) {
            throw new DuplicateDepartmentException("código", code);
        }
    }

    /**
     * Recorta los extremos sin alterar espacios interiores; rechaza longitudes fuera de 2–120.
     *
     * @param name nombre no nulo validado en la entrada
     * @return nombre normalizado
     * @throws InvalidDepartmentException si la longitud tras trim no es válida
     */
    private String normalizeName(String name) {
        String normalized = name.trim();
        if (normalized.length() < 2 || normalized.length() > 120) {
            throw new InvalidDepartmentException("El nombre debe contener entre 2 y 120 caracteres");
        }
        return normalized;
    }

    /**
     * Canonicaliza el código sin depender del locale del servidor.
     *
     * @param code código no nulo validado en la entrada
     * @return código recortado y en mayúsculas con Locale.ROOT
     */
    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Separa el contrato público de JPA y de sus fechas internas.
     *
     * @param department entidad persistida
     * @return proyección pública
     */
    private DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                department.getCode(),
                department.getDescription(),
                department.isActive());
    }
}
