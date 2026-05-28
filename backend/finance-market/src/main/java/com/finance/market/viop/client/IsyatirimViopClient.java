package com.finance.market.viop.client;

import com.finance.common.exception.ExternalApiException;
import com.finance.market.viop.config.ViopProperties;
import com.finance.market.viop.dto.ViopContractSpec;
import com.finance.market.viop.dto.ViopHistoryPoint;
import com.finance.market.viop.dto.ViopQuoteSnapshot;
import com.finance.market.viop.dto.external.OneEndeksDto;
import com.finance.market.viop.dto.external.ViopChartDataResponse;
import com.finance.market.viop.dto.external.ViopContractMetadataResponse;
import com.finance.market.viop.dto.external.ViopFutureMetadataDto;
import com.finance.market.viop.dto.external.ViopOptionMetadataDto;
import com.finance.market.viop.mapper.ViopHtmlSnapshotParser;
import com.finance.market.viop.mapper.ViopMetadataMapper;
import com.finance.market.viop.mapper.ViopSnapshotMapper;
import com.finance.market.viop.model.ViopHistoryResolution;
import com.finance.market.viop.port.ViopMarketDataPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * İş Yatırım-backed {@link ViopMarketDataPort}: contract metadata and chart data come from JSON
 * endpoints while bulk live quotes are scraped from the analysis page HTML. Calls carry a session
 * cookie (refreshed once on 401/403) and are guarded by circuit-breaker/retry/rate-limiter.
 */
@Log4j2
@Component
public class IsyatirimViopClient implements ViopMarketDataPort {

    private static final String RESILIENCE_NAME = "viop";
    private static final DateTimeFormatter CHART_TS_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Europe/Istanbul"));
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final WebClient viopWebClient;
    private final IsyatirimSessionManager sessionManager;
    private final ViopProperties properties;
    private final ViopMetadataMapper metadataMapper;
    private final ViopSnapshotMapper snapshotMapper;
    private final ViopHtmlSnapshotParser htmlParser;

    public IsyatirimViopClient(@Qualifier("viopWebClient") WebClient viopWebClient,
                               IsyatirimSessionManager sessionManager,
                               ViopProperties properties,
                               ViopMetadataMapper metadataMapper,
                               ViopSnapshotMapper snapshotMapper,
                               ViopHtmlSnapshotParser htmlParser) {
        this.viopWebClient = viopWebClient;
        this.sessionManager = sessionManager;
        this.properties = properties;
        this.metadataMapper = metadataMapper;
        this.snapshotMapper = snapshotMapper;
        this.htmlParser = htmlParser;
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_NAME)
    @Retry(name = RESILIENCE_NAME)
    @RateLimiter(name = RESILIENCE_NAME)
    public List<ViopContractSpec> fetchFutureContractSpecs() {
        log.debug("Fetching VIOP future contract metadata");
        ViopContractMetadataResponse<ViopFutureMetadataDto> response = getWithSession(
                uriBuilder -> uriBuilder.path(properties.vadeliMetadataPath()).build(),
                new ParameterizedTypeReference<ViopContractMetadataResponse<ViopFutureMetadataDto>>() { }
        );
        if (response == null || response.value() == null) return List.of();
        return response.value().stream().map(metadataMapper::toFutureSpec).toList();
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_NAME)
    @Retry(name = RESILIENCE_NAME)
    @RateLimiter(name = RESILIENCE_NAME)
    public List<ViopContractSpec> fetchOptionContractTemplates() {
        log.debug("Fetching VIOP option metadata templates");
        ViopContractMetadataResponse<ViopOptionMetadataDto> response = getWithSession(
                uriBuilder -> uriBuilder.path(properties.opsiyonMetadataPath()).build(),
                new ParameterizedTypeReference<ViopContractMetadataResponse<ViopOptionMetadataDto>>() { }
        );
        if (response == null || response.value() == null) return List.of();
        return response.value().stream().map(metadataMapper::toOptionTemplateSpec).toList();
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_NAME)
    @Retry(name = RESILIENCE_NAME)
    @RateLimiter(name = RESILIENCE_NAME)
    public List<ViopQuoteSnapshot> fetchAllLiveSnapshots() {
        log.debug("Fetching VIOP bulk live snapshot HTML");
        String html;
        try {
            String cookieHeader = sessionManager.currentCookieHeader();
            html = viopWebClient.get()
                    .uri(properties.viopAnalysisPagePath())
                    .headers(headers -> {
                        if (cookieHeader != null && !cookieHeader.isBlank()) {
                            headers.add(HttpHeaders.COOKIE, cookieHeader);
                        }
                        headers.add(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE);
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(properties.requestTimeout());
        } catch (WebClientResponseException e) {
            throw mapToExternal(e, "VIOP_BULK_SNAPSHOT");
        }
        if (html == null || html.isBlank()) return List.of();
        return htmlParser.parse(html);
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_NAME)
    @Retry(name = RESILIENCE_NAME)
    @RateLimiter(name = RESILIENCE_NAME)
    public ViopQuoteSnapshot fetchSnapshot(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("VIOP symbol must not be blank");
        }
        log.debug("Fetching VIOP OneEndeks snapshot for {}", symbol);
        List<OneEndeksDto> response = getWithSession(
                uriBuilder -> uriBuilder.path(properties.oneEndeksPath())
                        .queryParam("endeks", symbol)
                        .build(),
                new ParameterizedTypeReference<List<OneEndeksDto>>() { }
        );
        if (response == null || response.isEmpty()) {
            throw new ExternalApiException("VIOP_ONE_ENDEKS",
                    "OneEndeks returned empty payload for symbol " + symbol);
        }
        return snapshotMapper.fromOneEndeks(response.get(0));
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_NAME)
    @Retry(name = RESILIENCE_NAME)
    @RateLimiter(name = RESILIENCE_NAME)
    public List<ViopHistoryPoint> fetchHistory(String symbol, ViopHistoryResolution resolution,
                                               Instant from, Instant to) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("VIOP symbol must not be blank");
        }
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("Invalid time range: from=" + from + " to=" + to);
        }
        log.debug("Fetching VIOP history symbol={}, period={}min, from={}, to={}",
                symbol, resolution.periodMinutes(), from, to);
        String fromStr = CHART_TS_FMT.format(from.atZone(ISTANBUL));
        String toStr = CHART_TS_FMT.format(to.atZone(ISTANBUL));
        ViopChartDataResponse response = getWithSession(
                uriBuilder -> uriBuilder.path(properties.chartDataPath())
                        .queryParam("period", resolution.periodMinutes())
                        .queryParam("from", fromStr)
                        .queryParam("to", toStr)
                        .queryParam("endeks", symbol)
                        .build(),
                new ParameterizedTypeReference<ViopChartDataResponse>() { }
        );
        if (response == null || response.data() == null || response.data().isEmpty()) {
            return Collections.emptyList();
        }
        List<ViopHistoryPoint> points = new ArrayList<>(response.data().size());
        for (List<BigDecimal> entry : response.data()) {
            if (entry == null || entry.size() < 2) continue;
            long epochMs = entry.get(0).longValue();
            BigDecimal close = entry.get(1);
            LocalDateTime candleDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ISTANBUL);
            points.add(new ViopHistoryPoint(candleDate, close));
        }
        return points;
    }

    /** Executes a session-cookie GET, refreshing the session and retrying once on 401/403. */
    private <T> T getWithSession(Function<UriBuilder, java.net.URI> uri,
                                 ParameterizedTypeReference<T> typeRef) {
        try {
            return executeGet(uri, typeRef);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                log.warn("VIOP request rejected (status={}); refreshing session and retrying once",
                        e.getStatusCode());
                sessionManager.forceRefresh();
                try {
                    return executeGet(uri, typeRef);
                } catch (WebClientResponseException retryFail) {
                    throw mapToExternal(retryFail, "VIOP_REQUEST");
                }
            }
            throw mapToExternal(e, "VIOP_REQUEST");
        } catch (Exception e) {
            throw new ExternalApiException("VIOP_REQUEST",
                    "VIOP request failed: " + e.getMessage(), e);
        }
    }

    private <T> T executeGet(Function<UriBuilder, java.net.URI> uri, ParameterizedTypeReference<T> typeRef) {
        String cookieHeader = sessionManager.currentCookieHeader();
        return viopWebClient.get()
                .uri(uri)
                .headers(headers -> {
                    if (cookieHeader != null && !cookieHeader.isBlank()) {
                        headers.add(HttpHeaders.COOKIE, cookieHeader);
                    }
                    headers.add(HttpHeaders.REFERER, properties.baseUrl() + properties.viopAnalysisPagePath());
                    headers.add("X-Requested-With", "XMLHttpRequest");
                    headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                })
                .retrieve()
                .bodyToMono(typeRef)
                .block(properties.requestTimeout());
    }

    private ExternalApiException mapToExternal(WebClientResponseException e, String code) {
        return new ExternalApiException(code,
                "İş Yatırım VIOP request failed status=" + e.getStatusCode() + " body=" + e.getResponseBodyAsString(),
                e);
    }
}
