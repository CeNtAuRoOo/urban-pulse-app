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
        when(departmentRepository.findById(id)).thenReturn(Optional.of(existing));
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
