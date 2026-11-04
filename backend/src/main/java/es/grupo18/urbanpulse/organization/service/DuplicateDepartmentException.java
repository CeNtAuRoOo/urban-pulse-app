package es.grupo18.urbanpulse.organization.service;

/** Conflicto de unicidad del nombre o código de un departamento. */
public class DuplicateDepartmentException extends RuntimeException {

    /**
     * Describe el conflicto de unicidad del catálogo.
     *
     * @param field atributo duplicado
     * @param value valor público o descripción genérica de la carrera SQL
     */
    public DuplicateDepartmentException(String field, String value) {
        super("Ya existe un departamento con " + field + " '" + value + "'");
    }
}
