package com.finance.common.dto;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.finance.common.dto.response.PagedResponse;
import java.util.Collection;
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

    public static <T extends Collection<?>> ApiResponse<T> successOrEmpty(String message, String emptyMessage, T data) {
        String msg = (data == null || data.isEmpty()) ? emptyMessage : message;
        return new ApiResponse<>(true, msg, data);
    }

    public static <T> ApiResponse<PagedResponse<T>> successOrEmpty(
            String message, String emptyMessage, PagedResponse<T> data) {
        String msg = (data == null || data.content().isEmpty()) ? emptyMessage : message;
        return new ApiResponse<>(true, msg, data);
    }
}
