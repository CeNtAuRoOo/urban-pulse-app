package es.grupo18.urbanpulse.entity.enums;

/**
 * Define los roles disponibles en UrbanPulse.
 * Distingue entre roles globales, aplicables a toda la plataforma,
 * y departamentales, asociados a departamentos concretos.
 */
public enum RoleCode {
    CITIZEN,
    OPERATOR,
    ADMIN,
    DEPARTMENT_COORDINATOR,
    TECHNICIAN;

    /**
     * Comprueba si el rol tiene ámbito global.
     *
     * @return true si el rol es global; false en caso contrario
     */
    public boolean isGlobal() {
        return switch (this) {
            case CITIZEN, OPERATOR, ADMIN -> true;
            case DEPARTMENT_COORDINATOR, TECHNICIAN -> false;
        };
    }

    /**
     * Comprueba si el rol tiene ámbito departamental.
     *
     * @return true si el rol es departamental; false en caso contrario
     */
    public boolean isDepartmental() {
        return !isGlobal();
    }
}
