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

    @ExceptionHandler(DepartmentNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(DepartmentNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(DuplicateDepartmentException.class)
    ResponseEntity<ProblemDetail> handleDuplicate(DuplicateDepartmentException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidDepartmentException.class)
    ResponseEntity<ProblemDetail> handleInvalidDepartment(InvalidDepartmentException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
