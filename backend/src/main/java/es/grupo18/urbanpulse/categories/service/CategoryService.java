package es.grupo18.urbanpulse.categories.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.hibernate.exception.ConstraintViolationException;

import es.grupo18.urbanpulse.categories.dto.CategoryResponse;
import es.grupo18.urbanpulse.categories.dto.CreateCategoryRequest;
import es.grupo18.urbanpulse.categories.dto.PatchCategoryRequest;
import es.grupo18.urbanpulse.categories.model.Category;
import es.grupo18.urbanpulse.categories.repository.CategoryRepository;
import es.grupo18.urbanpulse.shared.error.ApiException;

/**
 * Reglas y transacciones del catálogo; conserva nombres reservados y serializa
 * PATCH por fila.
 */
@Service
public class CategoryService {
    private final CategoryRepository repository;

    /**
     * Configura el acceso transaccional al catálogo.
     *
     * @param repository repositorio con unicidad y bloqueo de escritura
     */
    public CategoryService(CategoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Lista todo el catálogo, incluidas categorías inactivas.
     *
     * @return proyecciones por nombre según PostgreSQL y UUID como desempate
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return repository.findAllByOrderByNameAscIdAsc().stream().map(this::toResponse).toList();
    }

    /**
     * Consulta una categoría sin filtrar su estado.
     *
     * @param id identidad persistida
     * @return los cuatro atributos públicos
     * @throws CategoryNotFoundException si la identidad no existe
     */
    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {
        return toResponse(repository.findById(id).orElseThrow(CategoryNotFoundException::new));
    }

    /**
     * Normaliza y valida el alta antes de persistir; las categorías inactivas
     * reservan su nombre.
     *
     * @param request datos de alta; descripción y active pueden ser null
     * @return categoría creada con UUIDv7 y active true si se omitió o era null
     * @throws DuplicateCategoryException si otra fila reserva el nombre, incluida
     *                                    una carrera SQL
     * @throws ApiException               si los datos incumplen los límites del
     *                                    catálogo
     */
    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = normalizeName(request.name());
        validateDescription(request.description());
        assertUnique(name, null);
        return persist(new Category(name, request.description(), request.active() == null || request.active()));
    }

    /**
     * Bloquea la fila hasta finalizar la transacción y modifica solo los campos
     * presentes.
     * El índice único protege también renombrados concurrentes; una infracción
     * revierte todo el cambio.
     *
     * @param id      identidad a conservar
     * @param request campos presentes; descripción vacía limpia el contenido
     * @return estado resultante, incluidos cambios concurrentes ya confirmados
     *         antes del bloqueo
     * @throws CategoryNotFoundException  si no existe la categoría
     * @throws DuplicateCategoryException si el nombre pertenece a otra categoría
     * @throws ApiException               si el parche está vacío o incumple las
     *                                    restricciones
     */
    @Transactional
    public CategoryResponse patch(UUID id, PatchCategoryRequest request) {
        if (!request.isNotEmpty()) {
            throw invalid("Debe proporcionar al menos un campo para actualizar.");
        }
        validateDescription(request.description());
        String requestedName = request.name() == null ? null : normalizeName(request.name());
        Category category = repository.findForUpdate(id).orElseThrow(CategoryNotFoundException::new);
        if (requestedName != null) {
            assertUnique(requestedName, id);
            category.setName(requestedName);
        }
        if (request.description() != null) {
            category.setDescription(request.description());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
        return persist(category);
    }

    /**
     * Fuerza la escritura dentro de la transacción para detectar carreras.
     * Solo SQLSTATE 23505 del índice de nombre se traduce a duplicado; las demás
     * causas se propagan.
     *
     * @param category entidad nueva o bloqueada
     * @return proyección de la entidad persistida
     * @throws DuplicateCategoryException si PostgreSQL rechaza un nombre ya
     *                                    reservado
     */
    private CategoryResponse persist(Category category) {
        try {
            return toResponse(repository.saveAndFlush(category));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "23505".equals(violation.getSQLState())
                        && "uk_categories_name_normalized".equals(violation.getConstraintName())) {
                    throw new DuplicateCategoryException();
                }
            }
            throw exception;
        }
    }

    /**
     * Comprueba el nombre con el mismo lower de PostgreSQL que usa el índice único.
     * La comprobación preventiva no sustituye la restricción SQL ante carreras.
     *
     * @param name nombre normalizado
     * @param id   identidad excluida al modificar; null para un alta
     * @throws DuplicateCategoryException si otra categoría, activa o inactiva,
     *                                    reserva el nombre
     */
    private void assertUnique(String name, UUID id) {
        boolean duplicate = id == null ? repository.existsByNormalizedName(name)
                : repository.existsByNormalizedNameAndIdNot(name, id);
        if (duplicate) {
            throw new DuplicateCategoryException();
        }
    }

    /**
     * Aplica String.trim, conserva espacios interiores y cuenta puntos de código
     * Unicode.
     *
     * @param name nombre de entrada, posiblemente null
     * @return nombre no blanco de 2 a 100 puntos de código
     * @throws ApiException si falta el nombre o incumple los límites
     */
    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (normalized.isBlank() || length < 2 || length > 100) {
            throw invalid("El nombre debe contener entre 2 y 100 caracteres.");
        }
        return normalized;
    }

    /**
     * Valida la descripción sin normalizarla ni recortarla.
     *
     * @param description texto opcional de hasta 500 puntos de código
     * @throws ApiException si supera el límite
     */
    private void validateDescription(String description) {
        if (description != null && description.codePointCount(0, description.length()) > 500) {
            throw invalid("La descripción no puede superar los 500 caracteres.");
        }
    }

    /**
     * Construye el error compartido de validación del catálogo.
     *
     * @param detail explicación pública sin datos de persistencia
     * @return error HTTP 422 VALIDATION_ERROR
     */
    private ApiException invalid(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", detail);
    }

    /**
     * Separa el modelo persistente del contrato público.
     *
     * @param category entidad que se proyecta
     * @return identidad, nombre, descripción nullable y estado
     */
    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
                category.isActive());
    }
}
