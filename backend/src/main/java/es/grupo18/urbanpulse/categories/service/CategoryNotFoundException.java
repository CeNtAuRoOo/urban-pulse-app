package es.grupo18.urbanpulse.categories.service;

import org.springframework.http.HttpStatus;

import es.grupo18.urbanpulse.shared.error.ApiException;

/** Recurso inexistente, expuesto como HTTP 404 CATEGORY_NOT_FOUND. */
public class CategoryNotFoundException extends ApiException {
    /** Crea la excepción con código y mensaje públicos. */
    public CategoryNotFoundException() {
        super(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "La categoría no existe.");
    }
}
