package com.finance.common.exception;
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
