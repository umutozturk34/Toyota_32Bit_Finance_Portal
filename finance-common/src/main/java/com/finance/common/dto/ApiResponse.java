package com.finance.common.dto;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.finance.common.dto.response.PagedResponse;
import java.util.Collection;
/**
 * Uniform success envelope for REST endpoints, carrying a {@code success} flag, optional message,
 * payload and a server timestamp. Use the static factories to construct instances.
 */
@Data
@NoArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data);
    }
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    /**
     * Always-successful response whose message is {@code emptyMessage} when the collection is null
     * or empty, otherwise {@code message}.
     */
    public static <T extends Collection<?>> ApiResponse<T> successOrEmpty(String message, String emptyMessage, T data) {
        String msg = (data == null || data.isEmpty()) ? emptyMessage : message;
        return new ApiResponse<>(true, msg, data);
    }

    /**
     * Paged variant of {@link #successOrEmpty(String, String, Collection)}: uses {@code emptyMessage}
     * when the page content is null or empty.
     */
    public static <T> ApiResponse<PagedResponse<T>> successOrEmpty(
            String message, String emptyMessage, PagedResponse<T> data) {
        String msg = (data == null || data.content().isEmpty()) ? emptyMessage : message;
        return new ApiResponse<>(true, msg, data);
    }
}
