package com.opspilot.common.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.opspilot.auth.DuplicateEmailException;
import com.opspilot.auth.InvalidCredentialsException;
import com.opspilot.workspace.InsufficientWorkspaceRoleException;
import com.opspilot.workspace.WorkspaceNotFoundException;
import com.opspilot.project.*;
import com.opspilot.task.*;
import com.opspilot.document.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpectedError(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", Map.of());
    }
    private ResponseEntity<ApiError> response(HttpStatus status, String message, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
                status.getReasonPhrase(), message, fieldErrors));
    }
}
