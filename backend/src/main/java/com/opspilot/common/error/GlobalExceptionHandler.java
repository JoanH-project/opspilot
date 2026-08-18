package com.opspilot.common.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.opspilot.auth.DuplicateEmailException;
import com.opspilot.auth.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }
    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ApiError> handleDuplicateEmail(DuplicateEmailException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }
    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException exception) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage(), Map.of());
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpectedError(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", Map.of());
    }
    private ResponseEntity<ApiError> response(HttpStatus status, String message, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
                status.getReasonPhrase(), message, fieldErrors));
    }
}
