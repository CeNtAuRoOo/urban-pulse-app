package es.grupo18.urbanpulse.exception;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.security.authentication.AuthenticationServiceException;

/**
 * Transporta un fallo del backend de autenticación hasta el manejador de
 * Security.
 * Conserva únicamente la categoría y el tipo de excepción: no adjunta mensajes
 * ni
 * causas JDBC que los logs internos de Spring podrían revelar.
 */
public class AuthenticationBackendException extends AuthenticationServiceException {

    private final boolean unavailable;
    private final String failureType;

    /**
     * Crea una excepción de autenticación a partir de un fallo interno.
     * Clasifica si corresponde a indisponibilidad de la base de datos
     * y conserva únicamente el tipo de la excepción original,
     * sin adjuntar mensajes ni causas potencialmente sensibles.
     *
     * @param failure excepción original producida por el backend
     *                de autenticación
     */
    public AuthenticationBackendException(RuntimeException failure) {
        super("Fallo del backend de autenticación.");
        this.unavailable = isDatabaseUnavailable(failure);
        this.failureType = failure.getClass().getName();
    }

    /**
     * Indica si el fallo de autenticación se debe a una indisponibilidad
     * de la base de datos.
     *
     * @return true si se ha identificado un fallo de disponibilidad;
     *         false en caso contrario
     */
    public boolean isUnavailable() {
        return unavailable;
    }

    /**
     * Obtiene el nombre del tipo de excepción que originó el fallo,
     * sin revelar su mensaje ni sus causas internas.
     *
     * @return nombre completo de la clase de la excepción original
     */
    public String failureType() {
        return failureType;
    }

    /**
     * Reconoce fallos de recursos/conexión y timeouts, incluidos los envueltos al
     * abrir o finalizar una transacción. No clasifica errores de sintaxis,
     * integridad o programación como indisponibilidad.
     *
     * @param failure excepción de persistencia o su envoltorio transaccional
     * @return true si hay evidencia tipada de indisponibilidad de PostgreSQL
     */
    public static boolean isDatabaseUnavailable(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof DataAccessResourceFailureException
                    || cause instanceof TransientDataAccessResourceException
                    || cause instanceof RecoverableDataAccessException
                    || cause instanceof QueryTimeoutException
                    || cause instanceof SQLTransientConnectionException
                    || cause instanceof SQLNonTransientConnectionException
                    || cause instanceof SQLRecoverableException
                    || cause instanceof SQLTimeoutException) {
                return true;
            }

            if (cause instanceof SQLException sql) {
                String state = sql.getSQLState();

                if (state != null
                        && (state.startsWith("08") || Set.of("53300", "57P01", "57P02", "57P03").contains(state))) {
                    return true;
                }
            }
        }

        return false;
    }
}
