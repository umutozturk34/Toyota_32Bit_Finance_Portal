package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
