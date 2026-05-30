package com.finance.notification.reports.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.finance.common.dto.response.PagedResponse;
import com.finance.notification.reports.dto.PerformanceSeriesPoint;
import com.finance.notification.reports.dto.PortfolioReportBundle;
import com.finance.notification.reports.dto.ReportAllocation;
import com.finance.notification.reports.dto.ReportPosition;
import com.finance.notification.reports.dto.ReportSummary;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * WebClient that fetches the portfolio view (summary, positions, allocation) and performance series
 * from the main backend on the caller's behalf (bearer token forwarded), assembling them into a
 * {@link PortfolioReportBundle} for PDF rendering.
 */
@Log4j2
@Component
public class PortfolioDataClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private final WebClient client;

    public PortfolioDataClient(WebClient.Builder builder,
                               @Value("${app.portfolioApi.baseUrl:http://backend:8080}") String baseUrl) {
        this.client = builder
                .baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    public PortfolioReportBundle fetch(Long portfolioId, String range, String accessToken) {
        ViewEnvelope view = getJson(
                "/api/v1/portfolios/" + portfolioId + "/view?include=summary,positions,allocation",
                accessToken,
                new ParameterizedTypeReference<ApiEnvelope<ViewEnvelope>>() {});
        List<PerformanceRecord> perf = getJson(
                "/api/v1/portfolios/" + portfolioId + "/chart?type=performance&range=" + range,
                accessToken,
                new ParameterizedTypeReference<ApiEnvelope<List<PerformanceRecord>>>() {});

        List<PerformanceSeriesPoint> series = (perf == null ? Collections.<PerformanceRecord>emptyList() : perf).stream()
                .map(p -> new PerformanceSeriesPoint(
                        p.timestamp(),
                        p.totalValueTry() != null ? p.totalValueTry().doubleValue() : 0d))
                .toList();

        ReportSummary summary = view != null ? view.summary() : null;
        List<ReportAllocation> allocation = view != null && view.allocation() != null ? view.allocation() : List.of();
        List<ReportPosition> positions = view != null && view.positions() != null && view.positions().content() != null
                ? view.positions().content() : List.of();

        return new PortfolioReportBundle(portfolioId, summary, allocation, positions, series);
    }

    private <T> T getJson(String path, String accessToken, ParameterizedTypeReference<ApiEnvelope<T>> type) {
        try {
            ApiEnvelope<T> envelope = client.get()
                    .uri(path)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (accessToken != null && !accessToken.isBlank()) {
                            h.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
                        }
                    })
                    .retrieve()
                    .bodyToMono(type)
                    .block(TIMEOUT);
            return Optional.ofNullable(envelope).map(ApiEnvelope::data).orElse(null);
        } catch (RuntimeException e) {
            log.error("PortfolioDataClient call failed path={} error={}", path, e.toString());
            throw e;
        }
    }

    /** Generic wrapper mirroring the backend's standard API response shape. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiEnvelope<T>(boolean success, String message, T data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ViewEnvelope(
            ReportSummary summary,
            PagedResponse<ReportPosition> positions,
            List<ReportAllocation> allocation
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PerformanceRecord(
            LocalDateTime timestamp,
            BigDecimal totalValueTry
    ) {}
}
