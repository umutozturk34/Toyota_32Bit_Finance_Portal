package com.finance.common.exception;
import com.finance.common.dto.ErrorResponse;
import com.finance.common.i18n.Translator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;
import java.util.Map;
/**
 * Application-wide REST exception handler that converts thrown exceptions into a uniform
 * {@link ErrorResponse} with a localized message (via {@link Translator}), a stable error code and
 * the matching HTTP status. Domain exceptions map to their semantic statuses (422/404/400/409/503);
 * framework validation/parse errors map to 400; and the catch-all {@code RuntimeException}/
 * {@code Exception} handlers return 500 without leaking internal detail.
 */
@Log4j2
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Translator translator;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
                translator.translateOrSelf(ex.getMessage(), ex.getMessageArgs()),
                ex.getErrorCode(),
                request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(error);
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
            translator.translateOrSelf(ex.getMessage(), ex.getMessageArgs()),
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
            translator.translateOrSelf(ex.getMessage(), ex.getMessageArgs()),
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
            translator.translate("error.illegalState", translator.translateOrSelf(ex.getMessage())),
            "ILLEGAL_STATE",
            request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            org.springframework.dao.OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic lock conflict at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
                translator.translate("error.concurrentUpdate"),
                "CONCURRENT_UPDATE",
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
            translator.translate("error.illegalArgument", translator.translateOrSelf(ex.getMessage())),
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
            translator.translate("error.validation"),
            "VALIDATION_ERROR",
            errors
        );
        error.setPath(request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> violations = ex.getConstraintViolations().stream()
                .collect(Collectors.toUnmodifiableMap(
                        v -> v.getPropertyPath().toString(),
                        v -> v.getMessage() == null ? "" : v.getMessage(),
                        (a, b) -> a));
        log.warn("Constraint violation: {}", violations);
        ErrorResponse error = ErrorResponse.of(
                translator.translate("error.validation"),
                "VALIDATION_ERROR",
                violations);
        error.setPath(request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body: {}", ex.getMostSpecificCause().getMessage());
        ErrorResponse error = ErrorResponse.of(
                translator.translate("error.malformedRequest"),
                "MALFORMED_REQUEST",
                request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing parameter: {}", ex.getParameterName());
        ErrorResponse error = ErrorResponse.of(
                translator.translate("error.missingParameter", ex.getParameterName()),
                "MISSING_PARAMETER",
                request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    @ExceptionHandler(TaskAlreadyRunningException.class)
    public ResponseEntity<ErrorResponse> handleTaskAlreadyRunning(TaskAlreadyRunningException ex, HttpServletRequest request) {
        log.warn("Task already running: {}", ex.getTaskType());
        ErrorResponse error = ErrorResponse.of(
                translator.translateOrSelf(ex.getMessage(), ex.getMessageArgs()),
                "TASK_ALREADY_RUNNING",
                request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);
    }

    @ExceptionHandler(SymbolNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSymbolNotFound(SymbolNotFoundException ex, HttpServletRequest request) {
        log.warn("Symbol not found: {}", ex.getSymbol());
        ErrorResponse error = ErrorResponse.of(
                translator.translateOrSelf(ex.getMessage(), ex.getMessageArgs()),
                "SYMBOL_NOT_FOUND",
                request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(error);
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApiException(ExternalApiException ex, HttpServletRequest request) {
        log.error("External API error [{}]: {}", ex.getServiceName(), ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
                translator.translate("error.externalApi", ex.getServiceName()),
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
            translator.translate("error.runtime"),
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
            translator.translate("error.unknown"),
            "UNKNOWN_ERROR",
            request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }
}
