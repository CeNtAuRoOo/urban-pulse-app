package es.grupo18.urbanpulse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.grupo18.urbanpulse.dto.CreateDepartmentRequest;
import es.grupo18.urbanpulse.dto.DepartmentResponse;
import es.grupo18.urbanpulse.dto.PatchDepartmentRequest;
import es.grupo18.urbanpulse.entity.Department;
import es.grupo18.urbanpulse.repository.DepartmentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Test
    void listsAndReadsExistingDepartments() {
        var department = new Department("Unidad", "UNIT", null, true);
        when(departmentRepository.findAllByOrderByNameAsc()).thenReturn(java.util.List.of(department));
        assertThat(departmentService.findAll()).extracting(DepartmentResponse::name).containsExactly("Unidad");
        UUID id = UUID.randomUUID();
        when(departmentRepository.findById(id)).thenReturn(Optional.of(department));
        assertThat(departmentService.findById(id).code()).isEqualTo("UNIT");
    }

    @Test
    void rejectsDuplicateNameAndMissingPatch() {
        when(departmentRepository.existsByNameIgnoreCase("Unidad")).thenReturn(true);
        assertThatThrownBy(() -> departmentService.create(new CreateDepartmentRequest("Unidad", "UNIT", null, true)))
                .isInstanceOf(DuplicateDepartmentException.class);
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> departmentService.patch(id, new PatchDepartmentRequest(null, null, null, false)))
                .isInstanceOf(DepartmentNotFoundException.class);
        verify(departmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void onlyKnownUniqueConstraintViolationsBecomeConflicts() {
        var request = new CreateDepartmentRequest("Unidad", "UNIT", null, true);
        var unique = new org.hibernate.exception.ConstraintViolationException("private SQL",
                new java.sql.SQLException("private SQL", "23505"), "uk_departments_code_normalized");
        when(departmentRepository.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("private", unique));
        assertThatThrownBy(() -> departmentService.create(request)).isInstanceOf(DuplicateDepartmentException.class);
        var other = new org.springframework.dao.DataIntegrityViolationException("other");
        org.mockito.Mockito.doThrow(other).when(departmentRepository).saveAndFlush(any());
        assertThatThrownBy(() -> departmentService.create(request)).isSameAs(other);
    }

    @Test
    void validatesNormalizedBounds() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String value : java.util.List.of("", " ", "a", "a".repeat(121))) {
                assertThat(validator.validate(new CreateDepartmentRequest(value, "OK", null, null))).isNotEmpty();
            }
            for (String value : java.util.List.of("", " ", "a", "a".repeat(21), "áá", "A B")) {
                assertThat(validator.validate(new CreateDepartmentRequest("Valid", value, null, null))).isNotEmpty();
            }
            assertThat(validator.validate(new CreateDepartmentRequest(" aA ", " ab ", "x".repeat(500), null))).isEmpty();
            assertThat(validator.validate(new CreateDepartmentRequest("a".repeat(120), "A".repeat(20), null, false))).isEmpty();
            assertThat(validator.validate(new PatchDepartmentRequest(null, null, null, null))).isNotEmpty();
        }
    }

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentService departmentService;

    @Test
    void createNormalizesNameAndCodeAndDefaultsToActive() {
        when(departmentRepository.saveAndFlush(any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DepartmentResponse result = departmentService.create(
                new CreateDepartmentRequest("  Mantenimiento Urbano  ", " mant ", "Descripción", null));

        assertThat(result.name()).isEqualTo("Mantenimiento Urbano");
        assertThat(result.code()).isEqualTo("MANT");
        assertThat(result.active()).isTrue();
        verify(departmentRepository).saveAndFlush(any(Department.class));
    }

    @Test
    void createRejectsDuplicateCode() {
        when(departmentRepository.existsByNameIgnoreCase("Limpieza")).thenReturn(false);
        when(departmentRepository.existsByCodeIgnoreCase("LIMP")).thenReturn(true);

        assertThatThrownBy(() -> departmentService.create(
                new CreateDepartmentRequest("Limpieza", "limp", null, true)))
                .isInstanceOf(DuplicateDepartmentException.class)
                .hasMessageContaining("código");

        verify(departmentRepository, never()).saveAndFlush(any(Department.class));
    }

    @Test
    void patchOnlyChangesProvidedFields() {
        UUID id = UUID.randomUUID();
        Department existing = new Department("Limpieza", "LIMP", "Original", true);
        when(departmentRepository.findForUpdate(id)).thenReturn(Optional.of(existing));
        when(departmentRepository.existsByNameIgnoreCaseAndIdNot("Limpieza", id)).thenReturn(false);
        when(departmentRepository.existsByCodeIgnoreCaseAndIdNot("LIMP", id)).thenReturn(false);
        when(departmentRepository.saveAndFlush(any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DepartmentResponse result = departmentService.patch(id,
                new PatchDepartmentRequest(null, null, "Actualizada", false));

        assertThat(result.name()).isEqualTo("Limpieza");
        assertThat(result.code()).isEqualTo("LIMP");
        assertThat(result.description()).isEqualTo("Actualizada");
        assertThat(result.active()).isFalse();
    }

    @Test
    void findByIdThrowsNotFoundForUnknownDepartment() {
        UUID id = UUID.randomUUID();
        when(departmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.findById(id))
                .isInstanceOf(DepartmentNotFoundException.class);
    }
}
