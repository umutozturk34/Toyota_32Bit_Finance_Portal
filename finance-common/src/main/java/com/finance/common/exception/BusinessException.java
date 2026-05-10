package com.finance.common.exception;
public class BusinessException extends RuntimeException {
    private final String errorCode;
    private final Object[] messageArgs;
    public BusinessException(String message) {
        super(message);
        this.errorCode = "BUSINESS_ERROR";
        this.messageArgs = new Object[0];
    }
    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
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
