package es.grupo18.urbanpulse.controller;

import es.grupo18.urbanpulse.dto.CreateCategoryRequest;
import es.grupo18.urbanpulse.dto.CategoryResponse;
import es.grupo18.urbanpulse.dto.PatchCategoryRequest;
import es.grupo18.urbanpulse.service.CategoryService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
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

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> findAll() {
        return categoryService.findAll();
    }

    @GetMapping("/{categoryId}")
    public CategoryResponse findById(@PathVariable UUID categoryId) {
        return categoryService.findById(categoryId);
    }

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

    @PatchMapping("/{categoryId}")
    public CategoryResponse patch(
            @PathVariable UUID categoryId,
            @Valid @RequestBody PatchCategoryRequest request) {
        return categoryService.patch(categoryId, request);
    }
}
