package com.finance.notification.reports;

import com.finance.common.config.AppProperties;
import com.finance.common.filter.RateLimitTier;
import io.github.bucket4j.Bandwidth;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Order(15)
public class PdfExportTier implements RateLimitTier {

    @Override
    public String name() {
        return "PDF_EXPORT";
    }

    @Override
    public boolean matches(String path, String method) {
        return "POST".equalsIgnoreCase(method) && path.startsWith("/api/v1/reports/portfolio-pdf");
    }

    @Override
    public Bandwidth toBandwidth(AppProperties.RateLimit rl) {
        return Bandwidth.builder()
                .capacity(rl.getPdfExportLimit())
                .refillIntervally(rl.getPdfExportLimit(), Duration.ofHours(1))
                .build();
    }

    @Override
    public String errorCode() {
        return "RATE_LIMIT_PDF_EXPORT_EXCEEDED";
    }

    @Override
    public String errorMessage() {
        return "error.rateLimit.pdfExport";
    }
}
