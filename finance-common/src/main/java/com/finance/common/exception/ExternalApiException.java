package com.finance.common.exception;
public class ExternalApiException extends RuntimeException {
    private final String serviceName;
    public ExternalApiException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
    }
    public ExternalApiException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
    }
    public String getServiceName() {
        return serviceName;
    }
}
