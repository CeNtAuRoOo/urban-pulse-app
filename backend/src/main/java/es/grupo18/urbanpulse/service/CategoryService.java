package es.grupo18.urbanpulse.service;

import es.grupo18.urbanpulse.dto.CategoryResponse;
import es.grupo18.urbanpulse.dto.CreateCategoryRequest;
import es.grupo18.urbanpulse.dto.PatchCategoryRequest;
import es.grupo18.urbanpulse.entity.Category;
import es.grupo18.urbanpulse.exception.ApiException;
import es.grupo18.urbanpulse.repository.CategoryRepository;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    private final CategoryRepository repository;

    public CategoryService(CategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return repository.findAllByOrderByNameAscIdAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {
        return toResponse(repository.findById(id).orElseThrow(CategoryNotFoundException::new));
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = normalizeName(request.name());
        validateDescription(request.description());
        assertUnique(name, null);
        return persist(new Category(name, request.description(), request.active() == null || request.active()));
    }

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

    private void assertUnique(String name, UUID id) {
        boolean duplicate = id == null ? repository.existsByNormalizedName(name)
                : repository.existsByNormalizedNameAndIdNot(name, id);
        if (duplicate) {
            throw new DuplicateCategoryException();
        }
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (normalized.isBlank() || length < 2 || length > 100) {
            throw invalid("El nombre debe contener entre 2 y 100 caracteres.");
        }
        return normalized;
    }

    private void validateDescription(String description) {
        if (description != null && description.codePointCount(0, description.length()) > 500) {
            throw invalid("La descripción no puede superar los 500 caracteres.");
        }
    }

    private ApiException invalid(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", detail);
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(), category.isActive());
    }
}
