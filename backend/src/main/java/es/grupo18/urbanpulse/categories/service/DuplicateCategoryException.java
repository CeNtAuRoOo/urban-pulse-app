package es.grupo18.urbanpulse.categories.service;

import org.springframework.http.HttpStatus;

import es.grupo18.urbanpulse.shared.error.ApiException;

/**
 * Nombre reservado por otra categoría, expuesto como HTTP 409
 * CATEGORY_DUPLICATE.
 */
public class DuplicateCategoryException extends ApiException {
    /** Crea la excepción con código y mensaje públicos. */
    public DuplicateCategoryException() {
        super(HttpStatus.CONFLICT, "CATEGORY_DUPLICATE", "Ya existe una categoría con ese nombre.");
    }
}
