package com.finance.notification.reports.client;

import com.finance.notification.reports.fx.ForexRatePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The client performs a single GET to /api/v1/market/history and maps the candle envelope into an
 * ASC-by-date {@link ForexRatePoint} series. Each test queues one response; the captured request is
 * inspected for URL and auth-header assertions.
 */
class ForexHistoryClientTest {

    private final AtomicReference<ClientRequest> lastRequest = new AtomicReference<>();
    private final List<ClientResponse> queuedResponses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lastRequest.set(null);
        queuedResponses.clear();
    }

    private ForexHistoryClient buildClient() {
        ExchangeFunction exchange = req -> {
            lastRequest.set(req);
            if (queuedResponses.isEmpty()) {
                return Mono.error(new IllegalStateException("no response queued"));
            }
            return Mono.just(queuedResponses.remove(0));
        };
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
        return new ForexHistoryClient(builder, "http://backend:8080");
    }

    @Test
    void should_mapCandlesAscendingByDate_when_envelopeHasData() {
        ForexHistoryClient client = buildClient();
        queuedResponses.add(jsonResponse("""
                {"success":true,"data":[
                  {"candleDate":"2026-01-03T00:00:00","sellingPrice":34.5},
                  {"candleDate":"2026-01-01T00:00:00","sellingPrice":34.1}
                ]}
                """));

        List<ForexRatePoint> points = client.fetchHistory("USD", "jwt.token");

        assertThat(points).hasSize(2);
        assertThat(points.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(points.get(0).rate()).isEqualByComparingTo("34.1");
        assertThat(points.get(1).date()).isEqualTo(LocalDate.of(2026, 1, 3));
    }

    @Test
    void should_skipCandlesWithNullDateOrPrice_when_envelopeHasGaps() {
        ForexHistoryClient client = buildClient();
        queuedResponses.add(jsonResponse("""
                {"success":true,"data":[
                  {"candleDate":null,"sellingPrice":34.5},
                  {"candleDate":"2026-01-02T00:00:00","sellingPrice":null},
                  {"candleDate":"2026-01-04T00:00:00","sellingPrice":35.0}
                ]}
                """));

        List<ForexRatePoint> points = client.fetchHistory("EUR", "tok");

        assertThat(points).hasSize(1);
        assertThat(points.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 4));
    }

    @Test
    void should_returnEmptyList_when_envelopeDataIsNull() {
        ForexHistoryClient client = buildClient();
        queuedResponses.add(jsonResponse("{\"success\":true,\"data\":null}"));

        List<ForexRatePoint> points = client.fetchHistory("USD", "tok");

        assertThat(points).isEmpty();
    }

    @Test
    void should_buildExpectedUrl_when_invoked() {
        ForexHistoryClient client = buildClient();
        queuedResponses.add(jsonResponse("{\"success\":true,\"data\":[]}"));

        client.fetchHistory("USD", "tok");

        assertThat(lastRequest.get().url().toString())
                .isEqualTo("http://backend:8080/api/v1/market/history?type=FOREX&code=USD&period=ALL");
    }

    @Test
    void should_attachBearerAuthHeader_when_accessTokenProvided() {
        ForexHistoryClient client = buildClient();
        queuedResponses.add(jsonResponse("{\"success\":true,\"data\":[]}"));

        client.fetchHistory("USD", "abc.token");

        assertThat(lastRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer abc.token");
    }

    @Test
    void should_omitAuthHeader_when_accessTokenIsBlank() {
        ForexHistoryClient client = buildClient();
        queuedResponses.add(jsonResponse("{\"success\":true,\"data\":[]}"));

        client.fetchHistory("USD", "   ");

        assertThat(lastRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void should_omitAuthHeader_when_accessTokenIsNull() {
        ForexHistoryClient client = buildClient();
        queuedResponses.add(jsonResponse("{\"success\":true,\"data\":[]}"));

        client.fetchHistory("USD", null);

        assertThat(lastRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void should_propagateException_when_upstreamFails() {
        ForexHistoryClient client = buildClient();
        queuedResponses.add(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                .body("boom")
                .build());

        assertThatThrownBy(() -> client.fetchHistory("USD", "tok"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void should_constructRecords_when_invokedDirectly() {
        ForexHistoryClient.ApiEnvelope<String> env =
                new ForexHistoryClient.ApiEnvelope<>(true, "ok", "payload");
        ForexHistoryClient.ForexCandle candle =
                new ForexHistoryClient.ForexCandle("2026-01-01T00:00:00", new java.math.BigDecimal("34.1"));

        assertThat(env.success()).isTrue();
        assertThat(env.data()).isEqualTo("payload");
        assertThat(candle.candleDate()).isEqualTo("2026-01-01T00:00:00");
        assertThat(candle.sellingPrice()).isEqualByComparingTo("34.1");
    }

    private ClientResponse jsonResponse(String body) {
        DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
        DataBuffer buffer = factory.wrap(body.getBytes(StandardCharsets.UTF_8));
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(Flux.just(buffer))
                .build();
    }
}
