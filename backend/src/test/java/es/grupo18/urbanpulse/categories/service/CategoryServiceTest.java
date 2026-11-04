package es.grupo18.urbanpulse.categories.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.grupo18.urbanpulse.categories.dto.CreateCategoryRequest;
import es.grupo18.urbanpulse.categories.dto.PatchCategoryRequest;
import es.grupo18.urbanpulse.categories.model.Category;
import es.grupo18.urbanpulse.categories.repository.CategoryRepository;
import es.grupo18.urbanpulse.shared.error.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock
    CategoryRepository repository;
    @InjectMocks
    CategoryService service;

    @Test
    void createsNormalizedNamesPreservingInteriorSpacesAndDefaults() {
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.create(new CreateCategoryRequest(" \tTráfico  urbano\r\n", null, null));
        assertThat(result.name()).isEqualTo("Tráfico  urbano");
        assertThat(result.description()).isNull();
        assertThat(result.active()).isTrue();
        var inactive = service.create(new CreateCategoryRequest("Otra", " descripción ", false));
        assertThat(inactive.active()).isFalse();
        assertThat(inactive.description()).isEqualTo(" descripción ");
    }

    @Test
    void enforcesBoundsBeforeAccessingPersistence() {
        for (String name : List.of("", " ", "x", "x".repeat(101), "\u2003\u2003", "🚦")) {
            assertThatThrownBy(() -> service.create(new CreateCategoryRequest(name, null, null)))
                    .isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> service.patch(UUID.randomUUID(), new PatchCategoryRequest(name, null, null)))
                    .isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> service.create(new CreateCategoryRequest(null, null, null)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create(new CreateCategoryRequest("Valid", "x".repeat(501), null)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.patch(UUID.randomUUID(), new PatchCategoryRequest()))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void validatesDtoBoundsAndEmptyPatchAfterNormalization() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new CreateCategoryRequest(" ab ", "x".repeat(500), null))).isEmpty();
            assertThat(validator.validate(new CreateCategoryRequest("x".repeat(100), null, false))).isEmpty();
            assertThat(validator.validate(new CreateCategoryRequest("🚦".repeat(100), "🚦".repeat(500), true)))
                    .isEmpty();
            assertThat(validator.validate(new CreateCategoryRequest("🚦", null, true))).isNotEmpty();
            assertThat(validator.validate(new CreateCategoryRequest("x".repeat(101), null, false))).isNotEmpty();
            assertThat(validator.validate(new PatchCategoryRequest())).isNotEmpty();
            assertThat(validator.validate(new PatchCategoryRequest(null, "", null))).isEmpty();
            assertThat(validator.validate(new PatchCategoryRequest(" ", null, null))).isNotEmpty();
        }
    }

    @Test
    void rejectsDuplicateCreateAndRenameWithoutMutatingEntity() {
        when(repository.existsByNormalizedName("Existing")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CreateCategoryRequest("Existing", null, true)))
                .isInstanceOf(DuplicateCategoryException.class);
        UUID id = UUID.randomUUID();
        var existing = new Category("Original", null, true);
        when(repository.findForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.existsByNormalizedNameAndIdNot("existing", id)).thenReturn(true);
        assertThatThrownBy(() -> service.patch(id, new PatchCategoryRequest("existing", "changed", false)))
                .isInstanceOf(DuplicateCategoryException.class);
        assertThat(existing.getName()).isEqualTo("Original");
        assertThat(existing.isActive()).isTrue();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void partialPatchesClearDescriptionDeactivateAndReactivateUnderLock() {
        UUID id = UUID.randomUUID();
        var existing = new Category("Original", "Description", true);
        when(repository.findForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var first = service.patch(id, new PatchCategoryRequest(null, null, false));
        assertThat(first.name()).isEqualTo("Original");
        assertThat(first.description()).isEqualTo("Description");
        assertThat(first.active()).isFalse();
        var second = service.patch(id, new PatchCategoryRequest("  Renamed  ", "", true));
        assertThat(second.name()).isEqualTo("Renamed");
        assertThat(second.description()).isEmpty();
        assertThat(second.active()).isTrue();
        verify(repository, times(2)).findForUpdate(id);
        verify(repository).existsByNormalizedNameAndIdNot("Renamed", id);
    }

    @Test
    void listsAndReadsInactiveCategoriesAndRejectsMissingResources() {
        var inactive = new Category("Inactive", null, false);
        UUID id = UUID.randomUUID();
        when(repository.findAllByOrderByNameAscIdAsc()).thenReturn(List.of(inactive));
        when(repository.findById(id)).thenReturn(Optional.of(inactive));
        assertThat(service.findAll())
                .extracting(category -> category.active())
                .containsExactly(false);
        assertThat(service.findById(id).active()).isFalse();
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.findById(missing)).isInstanceOf(CategoryNotFoundException.class);
        assertThatThrownBy(() -> service.patch(missing, new PatchCategoryRequest(null, null, true)))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void translatesOnlyKnownUniqueConstraintWithCorrectSqlState() {
        var request = new CreateCategoryRequest("Valid", null, true);
        var unique = new ConstraintViolationException("private SQL", new SQLException("private", "23505"),
                "uk_categories_name_normalized");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("private", unique));
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(DuplicateCategoryException.class)
                .hasMessageNotContaining("private");
        for (var cause : List.of(
                new ConstraintViolationException("private", new SQLException("private", "23505"), "other_index"),
                new ConstraintViolationException("private", new SQLException("private", "23514"),
                        "uk_categories_name_normalized"),
                new ConstraintViolationException("private", new SQLException("private", "23505"), (String) null))) {
            var failure = new DataIntegrityViolationException("private", cause);
            doThrow(failure).when(repository).saveAndFlush(any());
            assertThatThrownBy(() -> service.create(request)).isSameAs(failure);
        }
    }
}
