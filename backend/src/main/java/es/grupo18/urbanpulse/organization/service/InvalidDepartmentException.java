package es.grupo18.urbanpulse.organization.service;

/** Incumplimiento de una validación de negocio de departamentos. */
public class InvalidDepartmentException extends RuntimeException {

    /**
     * Comunica una validación de negocio.
     *
     * @param message explicación pública del incumplimiento
     */
    public InvalidDepartmentException(String message) {
        super(message);
    }
}
