package com.finance.common.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;
/**
 * Uniform error envelope returned by {@link com.finance.common.exception.GlobalExceptionHandler},
 * carrying a localized message, a stable machine-readable {@code errorCode}, the request path and an
 * optional per-field {@code validationErrors} map. Use the {@code of} factories to build instances.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private boolean success;
    private String message;
    private String errorCode;
    @Builder.Default 
    private LocalDateTime timestamp = LocalDateTime.now();
    private String path;
    @Builder.Default
    private String data = "null";
    private Map<String, String> validationErrors;
    public static ErrorResponse of(String message, String errorCode, String path) {
        return ErrorResponse.builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .path(path)
                .build();
    }
    public static ErrorResponse of(String message, String errorCode, Map<String, String> validationErrors) {
        return ErrorResponse.builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .validationErrors(validationErrors)
                .build();
    }
}
