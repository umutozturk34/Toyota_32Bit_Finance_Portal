package com.finance.backend.exception;
import com.finance.backend.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;
import java.util.Map;
@Log4j2
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        ErrorResponse error = ErrorResponse.of(ex.getMessage(), ex.getErrorCode(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(error);
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
            ex.getMessage(),
            "RESOURCE_NOT_FOUND",
            request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(error);
    }
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(BadRequestException ex, HttpServletRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
            ex.getMessage(),
            "BAD_REQUEST",
            request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex, HttpServletRequest request) {
        log.error("Illegal state: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
            "Operation not allowed: " + ex.getMessage(), 
            "ILLEGAL_STATE",
            request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.error("Illegal argument: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
            "Invalid argument: " + ex.getMessage(), 
            "INVALID_ARGUMENT",
            request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = ex.getBindingResult().getAllErrors().stream()
                .filter(FieldError.class::isInstance)
                .map(FieldError.class::cast)
                .collect(Collectors.toUnmodifiableMap(
                        FieldError::getField,
                        e -> e.getDefaultMessage() == null ? "" : e.getDefaultMessage(),
                        (a, b) -> a));
        log.warn("Validation failed: {}", errors);
        ErrorResponse error = ErrorResponse.of(
            "Validation failed", 
            "VALIDATION_ERROR",
            errors
        );
        error.setPath(request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }
    @ExceptionHandler(TaskAlreadyRunningException.class)
    public ResponseEntity<ErrorResponse> handleTaskAlreadyRunning(TaskAlreadyRunningException ex, HttpServletRequest request) {
        log.warn("Task already running: {}", ex.getTaskType());
        ErrorResponse error = ErrorResponse.of(
                ex.getMessage(),
                "TASK_ALREADY_RUNNING",
                request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApiException(ExternalApiException ex, HttpServletRequest request) {
        log.error("External API error [{}]: {}", ex.getServiceName(), ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
                "External service unavailable: " + ex.getServiceName(),
                "EXTERNAL_API_ERROR",
                request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        log.error("Runtime exception: {}", ex.getMessage(), ex);
        ErrorResponse error = ErrorResponse.of(
            "An unexpected error occurred",
            "INTERNAL_ERROR",
            request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected exception: {}", ex.getMessage(), ex);
        ErrorResponse error = ErrorResponse.of(
            "An unexpected error occurred. Please contact support.",
            "UNKNOWN_ERROR",
            request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }
}
