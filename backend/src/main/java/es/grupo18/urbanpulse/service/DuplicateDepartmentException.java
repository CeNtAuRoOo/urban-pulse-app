package es.grupo18.urbanpulse.service;

public class DuplicateDepartmentException extends RuntimeException {

    public DuplicateDepartmentException(String field, String value) {
        super("Ya existe un departamento con " + field + " '" + value + "'");
    }
}
