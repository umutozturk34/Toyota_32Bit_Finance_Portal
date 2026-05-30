package com.finance.notification.reports.service;

/** Raised when the portfolio PDF pipeline (rendering or the external pdf-service) fails. */
public class PdfGenerationException extends RuntimeException {

    public PdfGenerationException(String message) {
        super(message);
    }

    public PdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
