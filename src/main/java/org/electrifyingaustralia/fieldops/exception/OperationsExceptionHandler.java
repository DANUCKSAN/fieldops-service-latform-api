package org.electrifyingaustralia.fieldops.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class OperationsExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(
            ResourceNotFoundException exception
    ) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                exception.getMessage(),
                exception.getCode()
        );
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(ConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Operation conflict",
                exception.getMessage(),
                exception.getCode()
        );
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    ResponseEntity<ProblemDetail> handleLockConflict(
            CannotAcquireLockException exception
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Inventory is busy",
                "The inventory changed concurrently; retry the operation",
                "INVENTORY_BUSY"
        );
    }

    @ExceptionHandler(InvalidOperationException.class)
    ResponseEntity<ProblemDetail> handleInvalidOperation(
            InvalidOperationException exception
    ) {
        return problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Invalid operation",
                exception.getMessage(),
                exception.getCode()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        ProblemDetail detail = validationProblem();
        detail.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                fieldErrors.putIfAbsent(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                )
        );

        ProblemDetail detail = validationProblem();
        detail.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleMethodValidation(
            HandlerMethodValidationException exception
    ) {
        return ResponseEntity.badRequest().body(validationProblem());
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ProblemDetail> handleMalformedRequest(Exception exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "The request contains malformed or unsupported values",
                "INVALID_REQUEST"
        );
    }

    private ProblemDetail validationProblem() {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more request fields are invalid"
        );
        detail.setTitle("Request validation failed");
        detail.setProperty("code", "VALIDATION_ERROR");
        return detail;
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String message,
            String code
    ) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(title);
        detail.setProperty("code", code);
        return ResponseEntity.status(status).body(detail);
    }
}
