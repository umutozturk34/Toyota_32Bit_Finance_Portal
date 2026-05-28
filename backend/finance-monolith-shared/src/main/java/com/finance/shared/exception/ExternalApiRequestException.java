package com.finance.shared.exception;

/**
 * Raised when a call to an external/third-party API fails, carrying the originating service name so
 * handlers and logs can attribute the failure to a specific upstream.
 */
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
