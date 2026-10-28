package es.grupo18.urbanpulse.service;

import es.grupo18.urbanpulse.dto.CreateDepartmentRequest;
import es.grupo18.urbanpulse.dto.DepartmentResponse;
import es.grupo18.urbanpulse.dto.PatchDepartmentRequest;
import es.grupo18.urbanpulse.entity.Department;
import es.grupo18.urbanpulse.repository.DepartmentRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> findAll() {
        return departmentRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse findById(UUID id) {
        return toResponse(findEntity(id));
    }

    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        String name = normalizeName(request.name());
        String code = normalizeCode(request.code());
        assertUnique(name, code, null);

        Department department = new Department(name, code, request.description(),
                request.active() == null || request.active());
        return persist(department);
    }

    @Transactional
    public DepartmentResponse patch(UUID id, PatchDepartmentRequest request) {
        Department department = findEntity(id);
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

    private DepartmentResponse persist(Department department) {
        try {
            return toResponse(departmentRepository.saveAndFlush(department));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateDepartmentException("nombre o código", "indicado");
        }
    }

    private Department findEntity(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new DepartmentNotFoundException(id));
    }

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

    private String normalizeName(String name) {
        String normalized = name.trim();
        if (normalized.length() < 2 || normalized.length() > 120) {
            throw new InvalidDepartmentException("El nombre debe contener entre 2 y 120 caracteres");
        }
        return normalized;
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                department.getCode(),
                department.getDescription(),
                department.isActive());
    }
}
