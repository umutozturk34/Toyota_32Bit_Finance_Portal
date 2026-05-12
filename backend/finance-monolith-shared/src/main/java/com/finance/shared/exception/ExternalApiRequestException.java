package com.finance.shared.exception;

public class ExternalApiRequestException extends RuntimeException {
    private final String serviceName;

    public ExternalApiRequestException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
