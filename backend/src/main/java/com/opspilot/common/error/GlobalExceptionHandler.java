package com.opspilot.common.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.opspilot.auth.DuplicateEmailException;
import com.opspilot.auth.InvalidCredentialsException;
import com.opspilot.auth.ratelimit.AuthenticationProtectionUnavailableException;
import com.opspilot.auth.ratelimit.TooManyAuthenticationAttemptsException;
import com.opspilot.activity.InvalidActivityLimitException;
import com.opspilot.workspace.InsufficientWorkspaceRoleException;
import com.opspilot.workspace.WorkspaceNotFoundException;
import com.opspilot.project.*;
import com.opspilot.task.*;
import com.opspilot.document.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice(basePackages = "com.opspilot")
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
    @ExceptionHandler(TooManyAuthenticationAttemptsException.class)
    ResponseEntity<ApiError> handleRateLimit(TooManyAuthenticationAttemptsException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(new ApiError(Instant.now(), HttpStatus.TOO_MANY_REQUESTS.value(),
                        HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), exception.getMessage(), Map.of()));
    }
    @ExceptionHandler({AuthenticationProtectionUnavailableException.class, DataAccessException.class,
            TransactionException.class})
    ResponseEntity<ApiError> handleDependencyUnavailable(Exception exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE,
                "A required service is temporarily unavailable", Map.of());
    }
    @ExceptionHandler(WorkspaceNotFoundException.class)
    ResponseEntity<ApiError> handleWorkspaceNotFound(WorkspaceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }
    @ExceptionHandler(InsufficientWorkspaceRoleException.class)
    ResponseEntity<ApiError> handleInsufficientWorkspaceRole(InsufficientWorkspaceRoleException exception) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage(), Map.of());
    }
    @ExceptionHandler(ProjectNotFoundException.class)
    ResponseEntity<ApiError> projectNotFound(ProjectNotFoundException e) { return response(HttpStatus.NOT_FOUND,e.getMessage(),Map.of()); }
    @ExceptionHandler(ProjectAccessDeniedException.class)
    ResponseEntity<ApiError> projectDenied(ProjectAccessDeniedException e) { return response(HttpStatus.FORBIDDEN,e.getMessage(),Map.of()); }
    @ExceptionHandler(ArchivedProjectException.class)
    ResponseEntity<ApiError> archivedProject(ArchivedProjectException e) { return response(HttpStatus.CONFLICT,e.getMessage(),Map.of()); }
    @ExceptionHandler(InvalidProjectUpdateException.class)
    ResponseEntity<ApiError> invalidProjectUpdate(InvalidProjectUpdateException e) { return response(HttpStatus.BAD_REQUEST,e.getMessage(),Map.of()); }
    @ExceptionHandler({TaskNotFoundException.class}) ResponseEntity<ApiError> taskNotFound(RuntimeException e){return response(HttpStatus.NOT_FOUND,e.getMessage(),Map.of());}
    @ExceptionHandler({InvalidTaskAssigneeException.class,InvalidTaskUpdateException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class}) ResponseEntity<ApiError> invalidTaskInput(Exception e){return response(HttpStatus.BAD_REQUEST,"Invalid request",Map.of());}
    @ExceptionHandler(DocumentNotFoundException.class) ResponseEntity<ApiError> documentNotFound(DocumentNotFoundException e){return response(HttpStatus.NOT_FOUND,e.getMessage(),Map.of());}
    @ExceptionHandler(DocumentAccessDeniedException.class) ResponseEntity<ApiError> documentDenied(DocumentAccessDeniedException e){return response(HttpStatus.FORBIDDEN,e.getMessage(),Map.of());}
    @ExceptionHandler(ArchivedDocumentException.class) ResponseEntity<ApiError> archivedDocument(ArchivedDocumentException e){return response(HttpStatus.CONFLICT,e.getMessage(),Map.of());}
    @ExceptionHandler(InvalidDocumentUpdateException.class) ResponseEntity<ApiError> invalidDocument(InvalidDocumentUpdateException e){return response(HttpStatus.BAD_REQUEST,e.getMessage(),Map.of());}
    @ExceptionHandler(InvalidActivityLimitException.class)
    ResponseEntity<ApiError> invalidActivityLimit(InvalidActivityLimitException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpectedError(Exception exception) {
        logger.error("Unhandled API exception", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", Map.of());
    }
    private ResponseEntity<ApiError> response(HttpStatus status, String message, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
                status.getReasonPhrase(), message, fieldErrors));
    }
}
