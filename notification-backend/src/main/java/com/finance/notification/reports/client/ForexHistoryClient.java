package com.finance.notification.reports.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.finance.notification.reports.fx.ForexRatePoint;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * WebClient that fetches a single currency's forex selling-rate history from the main backend (bearer
 * token forwarded). The returned {@link ForexRatePoint} series — ASC by date — feeds
 * {@link com.finance.notification.reports.fx.ReportFxConverter} so the PDF converts each lira figure at
 * its own historical daily rate, mirroring the on-screen charts.
 */
@Log4j2
@Component
public class ForexHistoryClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private final WebClient client;

    public ForexHistoryClient(WebClient.Builder builder,
                              @Value("${app.portfolioApi.baseUrl:http://backend:8080}") String baseUrl) {
        this.client = builder
                .baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /**
     * Fetches the full forex history for {@code currencyCode} (e.g. "USD", "EUR") and maps it to an
     * ASC-by-date series of {@link ForexRatePoint}. Each backend candle contributes the first 10 chars
     * of {@code candleDate} as the {@link LocalDate} and its {@code sellingPrice} as the rate.
     */
    public List<ForexRatePoint> fetchHistory(String currencyCode, String accessToken) {
        try {
            ApiEnvelope<List<ForexCandle>> envelope = client.get()
                    .uri(uri -> uri.path("/api/v1/market/history")
                            .queryParam("type", "FOREX")
                            .queryParam("code", currencyCode)
                            .queryParam("period", "ALL")
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (accessToken != null && !accessToken.isBlank()) {
                            h.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
                        }
                    })
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<List<ForexCandle>>>() {})
                    .block(TIMEOUT);
            List<ForexCandle> candles = Optional.ofNullable(envelope).map(ApiEnvelope::data).orElse(List.of());
            return candles.stream()
                    .filter(c -> c.candleDate() != null && c.sellingPrice() != null)
                    .map(c -> new ForexRatePoint(LocalDate.parse(c.candleDate().substring(0, 10)), c.sellingPrice()))
                    .sorted(Comparator.comparing(ForexRatePoint::date))
                    .toList();
        } catch (RuntimeException e) {
            log.error("ForexHistoryClient call failed currency={} error={}", currencyCode, e.toString());
            throw e;
        }
    }

    /** Generic wrapper mirroring the backend's standard API response shape. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiEnvelope<T>(boolean success, String message, T data) {}

    /** Minimal forex candle: only the date string and selling price are needed to build a rate point. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForexCandle(String candleDate, BigDecimal sellingPrice) {}
}
