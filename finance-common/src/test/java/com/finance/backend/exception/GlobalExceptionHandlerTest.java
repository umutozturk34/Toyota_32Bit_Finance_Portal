package com.finance.backend.exception;

import com.finance.backend.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/test");
    }

    @Test
    void businessExceptionMapsToUnprocessableEntityWithCustomErrorCode() {
        BusinessException ex = new BusinessException("duplicate portfolio name", "DUPLICATE_PORTFOLIO");

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("DUPLICATE_PORTFOLIO");
        assertThat(response.getBody().getMessage()).isEqualTo("duplicate portfolio name");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/test");
    }

    @Test
    void businessExceptionFallsBackToDefaultErrorCodeWhenNotSpecified() {
        BusinessException ex = new BusinessException("something went wrong");

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getErrorCode()).isEqualTo("BUSINESS_ERROR");
    }

    @Test
    void resourceNotFoundMapsToNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("portfolio not found: 42");

        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFoundException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void badRequestMapsToBadRequest() {
        BadRequestException ex = new BadRequestException("invalid asset type: XYZ");

        ResponseEntity<ErrorResponse> response = handler.handleBadRequestException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void illegalStateMapsToConflictWithOperationPrefix() {
        IllegalStateException ex = new IllegalStateException("market closed");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalStateException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getErrorCode()).isEqualTo("ILLEGAL_STATE");
        assertThat(response.getBody().getMessage()).isEqualTo("Operation not allowed: market closed");
    }

    @Test
    void illegalArgumentMapsToBadRequestWithInvalidPrefix() {
        IllegalArgumentException ex = new IllegalArgumentException("price must be positive");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid argument: price must be positive");
    }

    @Test
    void methodArgumentNotValidMapsToBadRequestWithPerFieldMessages() {
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "target");
        binding.addError(new FieldError("target", "assetCode", "must not be blank"));
        binding.addError(new FieldError("target", "quantity", "must be positive"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, binding);

        ResponseEntity<ErrorResponse> response = handler.handleValidationExceptions(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getValidationErrors())
                .containsEntry("assetCode", "must not be blank")
                .containsEntry("quantity", "must be positive");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/test");
    }

    @Test
    void taskAlreadyRunningMapsToConflict() {
        TaskAlreadyRunningException ex = new TaskAlreadyRunningException("CRYPTO_SNAPSHOT");

        ResponseEntity<ErrorResponse> response = handler.handleTaskAlreadyRunning(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getErrorCode()).isEqualTo("TASK_ALREADY_RUNNING");
    }

    @Test
    void externalApiMapsToServiceUnavailable() {
        ExternalApiException ex = new ExternalApiException("TEFAS", "timeout");

        ResponseEntity<ErrorResponse> response = handler.handleExternalApiException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getErrorCode()).isEqualTo("EXTERNAL_API_ERROR");
        assertThat(response.getBody().getMessage()).contains("TEFAS");
    }

    @Test
    void runtimeExceptionMapsToInternalServerErrorWithGenericMessage() {
        RuntimeException ex = new RuntimeException("database connection failed");

        ResponseEntity<ErrorResponse> response = handler.handleRuntimeException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).doesNotContain("database connection failed");
    }

    @Test
    void genericExceptionMapsToInternalServerErrorWithSupportMessage() {
        Exception ex = new Exception("boom");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getErrorCode()).isEqualTo("UNKNOWN_ERROR");
    }
}
