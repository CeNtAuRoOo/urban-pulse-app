package es.grupo18.urbanpulse.controller;

import es.grupo18.urbanpulse.service.DepartmentNotFoundException;
import es.grupo18.urbanpulse.service.DuplicateDepartmentException;
import es.grupo18.urbanpulse.service.InvalidDepartmentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final es.grupo18.urbanpulse.exception.ProblemDetailFactory problems;

    public ApiExceptionHandler(es.grupo18.urbanpulse.exception.ProblemDetailFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(DepartmentNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(DepartmentNotFoundException exception, jakarta.servlet.http.HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateDepartmentException.class)
    ResponseEntity<ProblemDetail> handleDuplicate(DuplicateDepartmentException exception, jakarta.servlet.http.HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "DEPARTMENT_DUPLICATE", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidDepartmentException.class)
    ResponseEntity<ProblemDetail> handleInvalidDepartment(InvalidDepartmentException exception, jakarta.servlet.http.HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", exception.getMessage(), request);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail, jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(problems.create(status, code, detail, request.getRequestURI()));
    }
}
