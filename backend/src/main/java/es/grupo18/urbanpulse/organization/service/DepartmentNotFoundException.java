package es.grupo18.urbanpulse.organization.service;

import java.util.UUID;

/** Ausencia de la unidad municipal solicitada. */
public class DepartmentNotFoundException extends RuntimeException {

    /**
     * Identifica la unidad solicitada que no existe.
     *
     * @param id UUID solicitado
     */
    public DepartmentNotFoundException(UUID id) {
        super("No existe el departamento con id " + id);
    }
}
