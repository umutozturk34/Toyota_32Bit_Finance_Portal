package com.finance.common.exception;
/**
 * Signals that a requested entity does not exist; the message is an i18n key resolved with
 * {@link #getMessageArgs()} and mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {
    private final Object[] messageArgs;
    public ResourceNotFoundException(String message) {
        super(message);
        this.messageArgs = new Object[0];
    }
    public ResourceNotFoundException(String messageKey, Object... messageArgs) {
        super(messageKey);
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
