package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PDF-export configuration: the upstream PDF service URL and request timeout, plus the frontend base
 * URL used to build asset links in reports. Blank/absent values fall back to local defaults.
 */
@ConfigurationProperties("app")
public record PdfExportProperties(
        Pdf pdf,
        String frontendBaseUrl
) {

    public PdfExportProperties {
        if (pdf == null) pdf = new Pdf(null, null);
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            frontendBaseUrl = "http://localhost:5173";
        }
    }

    public record Pdf(
            String serviceUrl,
            Integer requestTimeoutMs
    ) {
        public Pdf {
            if (serviceUrl == null || serviceUrl.isBlank()) {
                serviceUrl = "http://pdf-service:8080";
            }
            if (requestTimeoutMs == null || requestTimeoutMs <= 0) {
                requestTimeoutMs = 30000;
            }
        }
    }
}
