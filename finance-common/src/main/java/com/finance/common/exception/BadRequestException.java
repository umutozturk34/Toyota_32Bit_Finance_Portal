package com.finance.common.exception;
/**
 * Signals invalid client input; the message is an i18n key resolved with {@link #getMessageArgs()}
 * and mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 */
public class BadRequestException extends RuntimeException {
    private final Object[] messageArgs;
    public BadRequestException(String message) {
        super(message);
        this.messageArgs = new Object[0];
    }
    public BadRequestException(String messageKey, Object... messageArgs) {
        super(messageKey);
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
