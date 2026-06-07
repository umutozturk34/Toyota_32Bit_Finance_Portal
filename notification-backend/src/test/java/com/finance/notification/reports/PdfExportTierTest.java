package com.finance.notification.reports;

import com.finance.common.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PdfExportTierTest {

    private PdfExportTier tier;

    @BeforeEach
    void setUp() {
        tier = new PdfExportTier();
    }

    @Test
    void should_exposeNameAndErrorMetadata_when_queried() {
        assertThat(tier.name()).isEqualTo("PDF_EXPORT");
        assertThat(tier.errorCode()).isEqualTo("RATE_LIMIT_PDF_EXPORT_EXCEEDED");
        assertThat(tier.errorMessage()).isEqualTo("error.rateLimit.pdfExport");
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/reports/portfolio-pdf, true",
            "post, /api/v1/reports/portfolio-pdf/42, true",
            "GET, /api/v1/reports/portfolio-pdf, false",
            "POST, /api/v1/reports/other, false"
    })
    void should_matchOnlyPostPortfolioPdf_when_evaluatingRequests(String method, String path, boolean expected) {
        assertThat(tier.matches(path, method)).isEqualTo(expected);
    }

    @Test
    void should_buildBandwidthFromConfiguredLimit_when_toBandwidthCalled() {
        AppProperties.RateLimit rl = new AppProperties.RateLimit();
        rl.setPdfExportLimit(5);

        Bandwidth bandwidth = tier.toBandwidth(rl);

        assertThat(bandwidth.getCapacity()).isEqualTo(5L);
    }
}
