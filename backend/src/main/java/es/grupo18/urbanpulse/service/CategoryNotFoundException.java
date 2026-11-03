package es.grupo18.urbanpulse.service;

import es.grupo18.urbanpulse.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CategoryNotFoundException extends ApiException {
    public CategoryNotFoundException() {
        super(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "La categoría no existe.");
    }
}
