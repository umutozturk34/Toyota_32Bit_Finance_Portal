package com.finance.common.exception;
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
