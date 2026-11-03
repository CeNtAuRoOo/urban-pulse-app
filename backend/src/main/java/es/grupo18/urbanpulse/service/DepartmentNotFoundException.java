package es.grupo18.urbanpulse.service;

import java.util.UUID;

public class DepartmentNotFoundException extends RuntimeException {

    public DepartmentNotFoundException(UUID id) {
        super("No existe el departamento con id " + id);
    }
}
