package es.grupo18.urbanpulse.categories.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import es.grupo18.urbanpulse.categories.dto.CategoryResponse;
import es.grupo18.urbanpulse.categories.dto.CreateCategoryRequest;
import es.grupo18.urbanpulse.categories.dto.PatchCategoryRequest;
import es.grupo18.urbanpulse.categories.service.CategoryService;

/**
 * Operaciones HTTP del catálogo, con autorización central y DTO independientes
 * de JPA.
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Configura el adaptador REST del catálogo.
     *
     * @param categoryService servicio transaccional
     */
    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * Consulta el catálogo con correo verificado.
     *
     * @return categorías activas e inactivas ordenadas por nombre y UUID
     */
    @GetMapping
    public List<CategoryResponse> findAll() {
        return categoryService.findAll();
    }

    /**
     * Consulta una categoría con correo verificado, aunque esté inactiva.
     *
     * @param categoryId UUID de ruta
     * @return proyección pública
     * @throws es.grupo18.urbanpulse.categories.service.CategoryNotFoundException
     *                                                                            si
     *                                                                            no
     *                                                                            existe
     *                                                                            la
     *                                                                            categoría
     */
    @GetMapping("/{categoryId}")
    public CategoryResponse findById(@PathVariable UUID categoryId) {
        return categoryService.findById(categoryId);
    }

    /**
     * Da de alta una categoría; la seguridad compartida exige ADMIN global
     * verificado.
     *
     * @param request datos normalizados y validados
     * @return HTTP 201 con proyección y URI absoluta en Location
     */
    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = categoryService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{categoryId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(location)
                .body(response);
    }

    /**
     * Modifica los campos presentes; exige ADMIN global verificado y conserva el
     * UUID.
     *
     * @param categoryId identidad del recurso
     * @param request    campos validados; omisión conserva y null JSON se rechaza
     * @return proyección pública actualizada
     */
    @PatchMapping("/{categoryId}")
    public CategoryResponse patch(
            @PathVariable UUID categoryId,
            @Valid @RequestBody PatchCategoryRequest request) {
        return categoryService.patch(categoryId, request);
    }
}
