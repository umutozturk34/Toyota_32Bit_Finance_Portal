package com.finance.shared.exception;

/**
 * Raised when a call to an external/third-party API fails; the message names the originating service
 * so handlers and logs can attribute the failure to a specific upstream.
 */
public class ExternalApiRequestException extends RuntimeException {
    public ExternalApiRequestException(String message) {
        super(message);
    }
}
