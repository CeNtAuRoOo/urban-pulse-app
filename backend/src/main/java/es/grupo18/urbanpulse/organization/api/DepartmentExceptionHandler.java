package es.grupo18.urbanpulse.organization.api;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.grupo18.urbanpulse.organization.service.DepartmentNotFoundException;
import es.grupo18.urbanpulse.organization.service.DuplicateDepartmentException;
import es.grupo18.urbanpulse.organization.service.InvalidDepartmentException;
import es.grupo18.urbanpulse.shared.error.ProblemDetailFactory;

/**
 * Traduce los errores específicos de Departamentos al formato común
 * ProblemDetail.
 */
@RestControllerAdvice
public class DepartmentExceptionHandler {

    private final ProblemDetailFactory problems;

    /**
     * Reutiliza el formato común de errores.
     *
     * @param problems fábrica compartida de ProblemDetail
     */
    public DepartmentExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    /**
     * Traduce la ausencia de una unidad.
     *
     * @param exception error de dominio
     * @param request   petición que proporciona la URI de instancia
     * @return HTTP 404 con DEPARTMENT_NOT_FOUND
     */
    @ExceptionHandler(DepartmentNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(DepartmentNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND", exception.getMessage(), request);
    }

    /**
     * Traduce duplicados detectados anticipadamente o por el índice SQL.
     *
     * @param exception error de dominio
     * @param request   petición que proporciona la URI de instancia
     * @return HTTP 409 con DEPARTMENT_DUPLICATE
     */
    @ExceptionHandler(DuplicateDepartmentException.class)
    ResponseEntity<ProblemDetail> handleDuplicate(DuplicateDepartmentException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "DEPARTMENT_DUPLICATE", exception.getMessage(), request);
    }

    /**
     * Traduce una validación de negocio del catálogo.
     *
     * @param exception error de dominio
     * @param request   petición que proporciona la URI de instancia
     * @return HTTP 422 con VALIDATION_ERROR
     */
    @ExceptionHandler(InvalidDepartmentException.class)
    ResponseEntity<ProblemDetail> handleInvalidDepartment(InvalidDepartmentException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", exception.getMessage(), request);
    }

    /**
     * Asocia el error de dominio con la URI que lo originó.
     *
     * @param status  estado HTTP
     * @param code    código estable
     * @param detail  explicación pública
     * @param request petición de origen
     * @return respuesta de error uniforme
     */
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(problems.create(status, code, detail, request.getRequestURI()));
    }
}
