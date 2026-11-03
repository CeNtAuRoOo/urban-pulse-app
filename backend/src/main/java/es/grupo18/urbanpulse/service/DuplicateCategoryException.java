package es.grupo18.urbanpulse.service;

import es.grupo18.urbanpulse.exception.ApiException;
import org.springframework.http.HttpStatus;

public class DuplicateCategoryException extends ApiException {
    public DuplicateCategoryException() {
        super(HttpStatus.CONFLICT, "CATEGORY_DUPLICATE", "Ya existe una categoría con ese nombre.");
    }
}
