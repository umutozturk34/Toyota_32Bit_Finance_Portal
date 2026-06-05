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
import java.util.ArrayList;
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
    /**
     * Maximum positions per HTTP page when paginating through {@code /positions}. The portfolio
     * backend caps {@code max-size} at 100 (see {@code portfolio.yaml}); we request the cap so the
     * loop typically completes in 1–2 round-trips even for power users with many closed lots.
     */
    private static final int POSITIONS_PAGE_SIZE = 100;
    /**
     * Safety brake. If the backend ever advertises an unbounded {@code totalPages}, stop after
     * 50 iterations (≈5,000 positions) to keep the PDF render bounded.
     */
    private static final int POSITIONS_MAX_PAGES = 50;
    private final WebClient client;

    public PortfolioDataClient(WebClient.Builder builder,
                               @Value("${app.portfolioApi.baseUrl:http://backend:8080}") String baseUrl) {
        this.client = builder
                .baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    public PortfolioReportBundle fetch(Long portfolioId, String range, String accessToken) {
        // /view is the single-shot read for the headline figures (summary) and the regular
        // allocation pie (Dağılım). Its positions slice is paginated and capped — the PDF must
        // show every lot, so we don't trust it here and rebuild positions via the paginated
        // endpoint below.
        ViewEnvelope view = getJson(
                "/api/v1/portfolios/" + portfolioId + "/view?include=summary,allocation",
                accessToken,
                new ParameterizedTypeReference<ApiEnvelope<ViewEnvelope>>() {});
        List<ReportAllocation> realizedAllocation = Optional.ofNullable(getJson(
                "/api/v1/portfolios/" + portfolioId + "/allocation?mode=realizedPnl",
                accessToken,
                new ParameterizedTypeReference<ApiEnvelope<List<ReportAllocation>>>() {}))
                .orElse(List.of());
        List<ReportPosition> positions = fetchAllPositions(portfolioId, accessToken);
        List<PerformanceRecord> perf = getJson(
                "/api/v1/portfolios/" + portfolioId + "/chart?type=performance&range=" + range,
                accessToken,
                new ParameterizedTypeReference<ApiEnvelope<List<PerformanceRecord>>>() {});

        List<PerformanceRecord> safePerf = perf == null ? Collections.emptyList() : perf;
        List<PerformanceSeriesPoint> series = safePerf.stream()
                .map(p -> new PerformanceSeriesPoint(
                        p.timestamp(),
                        p.totalValueTry() != null ? p.totalValueTry().doubleValue() : 0d))
                .toList();
        // Cost-based cumulative return % straight from the portfolio's pnlPercent — NOT a value index
        // (value/first − 1), which lot additions over time would distort. Leading synthetic-zero
        // points are trimmed (see #trimLeadingZeroReturn) so the curve fills the chart instead of
        // being squeezed into the tail.
        List<PerformanceSeriesPoint> returnSeries = trimLeadingZeroReturn(safePerf).stream()
                .map(p -> new PerformanceSeriesPoint(
                        p.timestamp(),
                        p.pnlPercent() != null ? p.pnlPercent().doubleValue() : 0d))
                .toList();

        ReportSummary summary = view != null ? view.summary() : null;
        List<ReportAllocation> allocation = view != null && view.allocation() != null ? view.allocation() : List.of();

        return new PortfolioReportBundle(portfolioId, summary, allocation, realizedAllocation, positions, series, returnSeries);
    }

    /**
     * Trims the leading run of synthetic-zero return points from the performance series. The 5Y
     * window often opens with snapshots whose {@code pnlPercent} is guarded to exactly zero — dates
     * before any lot was held, or older snapshots predating cost-basis tracking. Those flat-zero
     * leaders squeeze the meaningful return curve into the chart's tail, making it look as if only
     * the last weeks are plotted. Drop them, but keep the single 0% point immediately before the
     * first real return so the line still starts from baseline. The value series is left untouched:
     * its leading zeros are a genuine "not yet invested" value, not an artifact.
     */
    private static List<PerformanceRecord> trimLeadingZeroReturn(List<PerformanceRecord> perf) {
        int firstReal = -1;
        for (int i = 0; i < perf.size(); i++) {
            BigDecimal pct = perf.get(i).pnlPercent();
            if (pct != null && pct.signum() != 0) {
                firstReal = i;
                break;
            }
        }
        if (firstReal <= 0) return perf;
        return perf.subList(firstReal - 1, perf.size());
    }

    /**
     * Walks {@code /positions} page by page and concatenates the rows. The portfolio backend
     * paginates at the API boundary (default page size 10, max 100); the PDF must cover EVERY lot
     * regardless of count, so we iterate until {@code totalPages} is exhausted instead of trusting
     * a single oversized request (the backend clamps {@code size} to {@code max-size}).
     */
    private List<ReportPosition> fetchAllPositions(Long portfolioId, String accessToken) {
        List<ReportPosition> all = new ArrayList<>();
        int page = 0;
        while (page < POSITIONS_MAX_PAGES) {
            PagedResponse<ReportPosition> response = getJson(
                    "/api/v1/portfolios/" + portfolioId + "/positions?page=" + page
                            + "&size=" + POSITIONS_PAGE_SIZE,
                    accessToken,
                    new ParameterizedTypeReference<ApiEnvelope<PagedResponse<ReportPosition>>>() {});
            if (response == null || response.content() == null || response.content().isEmpty()) break;
            all.addAll(response.content());
            page++;
            if (page >= response.totalPages()) break;
        }
        log.debug("Fetched {} positions across {} page(s) for portfolio {}", all.size(), page, portfolioId);
        return all;
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

    /**
     * Slim envelope: the view endpoint is invoked with {@code include=summary,allocation} now —
     * positions come from the dedicated paginated endpoint so the PDF can include every lot,
     * not just the first page the view defaulted to (10 rows).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ViewEnvelope(
            ReportSummary summary,
            List<ReportAllocation> allocation
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PerformanceRecord(
            LocalDateTime timestamp,
            BigDecimal totalValueTry,
            BigDecimal pnlPercent
    ) {}
}
