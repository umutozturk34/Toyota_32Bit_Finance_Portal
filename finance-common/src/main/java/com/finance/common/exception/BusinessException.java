package com.finance.common.exception;
/**
 * Unchecked exception for domain rule violations. The message is treated as an i18n key:
 * {@link GlobalExceptionHandler} resolves it with {@link #getMessageArgs()} and returns HTTP 422
 * carrying {@link #getErrorCode()} (default {@code BUSINESS_ERROR}).
 */
public class BusinessException extends RuntimeException {
    private final String errorCode;
    private final Object[] messageArgs;
    public BusinessException(String message) {
        super(message);
        this.errorCode = "BUSINESS_ERROR";
        this.messageArgs = new Object[0];
    }
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "BUSINESS_ERROR";
        this.messageArgs = new Object[0];
    }
    public BusinessException(String messageKey, Object... messageArgs) {
        super(messageKey);
        this.errorCode = "BUSINESS_ERROR";
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
    public String getErrorCode() {
        return errorCode;
    }
    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
