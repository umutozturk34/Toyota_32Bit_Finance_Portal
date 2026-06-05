package com.finance.notification.reports.client;

import com.finance.common.dto.response.PagedResponse;
import com.finance.notification.reports.dto.PerformanceSeriesPoint;
import com.finance.notification.reports.dto.PortfolioReportBundle;
import com.finance.notification.reports.dto.ReportAllocation;
import com.finance.notification.reports.dto.ReportPosition;
import com.finance.notification.reports.dto.ReportSummary;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The client fetches data via four HTTP calls in order: /view → /allocation?mode=realizedPnl →
 * /positions (paginated) → /chart. Each test queues responses in that exact order. An empty
 * positions page short-circuits the pagination loop so most tests need exactly 4 responses.
 */
class PortfolioDataClientTest {

    private static final String EMPTY_POSITIONS_PAGE =
            "{\"success\":true,\"data\":{\"content\":[],\"page\":0,\"size\":100,\"totalElements\":0,\"totalPages\":0}}";

    private final AtomicReference<ClientRequest> lastRequest = new AtomicReference<>();
    private final List<ClientRequest> capturedRequests = new ArrayList<>();
    private final List<ClientResponse> queuedResponses = new ArrayList<>();

    private PortfolioDataClient buildClient() {
        ExchangeFunction exchange = req -> {
            lastRequest.set(req);
            capturedRequests.add(req);
            if (queuedResponses.isEmpty()) {
                return Mono.error(new IllegalStateException("no response queued"));
            }
            return Mono.just(queuedResponses.remove(0));
        };
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
        return new PortfolioDataClient(builder, "http://backend:8080");
    }

    /** Queue the standard 4 responses: view (nullable JSON), realized allocation, positions page, chart. */
    private void queueStandardResponses(String viewJson, String realizedJson, String positionsJson, String chartJson) {
        queuedResponses.add(jsonResponse(viewJson));
        queuedResponses.add(jsonResponse(realizedJson));
        queuedResponses.add(jsonResponse(positionsJson));
        queuedResponses.add(jsonResponse(chartJson));
    }

    @BeforeEach
    void setUp() {
        lastRequest.set(null);
        capturedRequests.clear();
        queuedResponses.clear();
    }

    @Test
    void should_returnBundleWithSummaryAllocationPositionsAndSeries_when_allEndpointsRespond() {
        PortfolioDataClient client = buildClient();
        queueStandardResponses(
                """
                {"success":true,"data":{
                  "summary":{"totalValueTry":1000,"pnlPercent":12.5},
                  "allocation":[{"label":"Stocks","assetType":"STOCK","valueTry":1000,"percent":100}]
                }}
                """,
                """
                {"success":true,"data":[
                  {"label":"STOCK","assetType":"STOCK","valueTry":50,"percent":100,"realizedPnlTry":50}
                ]}
                """,
                """
                {"success":true,"data":{"content":[{"id":1,"assetCode":"ASELS","assetName":"Aselsan"}],"page":0,"size":100,"totalElements":1,"totalPages":1}}
                """,
                """
                {"success":true,"data":[
                  {"timestamp":"2026-01-01T00:00:00","totalValueTry":1000},
                  {"timestamp":"2026-01-02T00:00:00","totalValueTry":1100}
                ]}
                """);

        PortfolioReportBundle bundle = client.fetch(42L, "1m", "jwt.token");

        assertThat(bundle.portfolioId()).isEqualTo(42L);
        assertThat(bundle.summary()).isNotNull();
        assertThat(bundle.summary().totalValueTry()).isEqualByComparingTo("1000");
        assertThat(bundle.allocation()).hasSize(1);
        assertThat(bundle.realizedAllocation()).hasSize(1);
        assertThat(bundle.realizedAllocation().get(0).realizedPnlTry()).isEqualByComparingTo("50");
        assertThat(bundle.positions()).hasSize(1);
        assertThat(bundle.performanceSeries()).hasSize(2);
        assertThat(bundle.performanceSeries().get(1).value()).isEqualTo(1100d);
    }

    @Test
    void should_attachBearerAuthHeader_when_accessTokenProvided() {
        PortfolioDataClient client = buildClient();
        queueStandardResponses("{\"success\":true,\"data\":null}", "{\"success\":true,\"data\":[]}", EMPTY_POSITIONS_PAGE, "{\"success\":true,\"data\":[]}");

        client.fetch(1L, "1m", "abc.token");

        ClientRequest first = capturedRequests.get(0);
        assertThat(first.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer abc.token");
    }

    @Test
    void should_omitAuthHeader_when_accessTokenIsBlank() {
        PortfolioDataClient client = buildClient();
        queueStandardResponses("{\"success\":true,\"data\":null}", "{\"success\":true,\"data\":[]}", EMPTY_POSITIONS_PAGE, "{\"success\":true,\"data\":[]}");

        client.fetch(1L, "1m", "   ");

        ClientRequest first = capturedRequests.get(0);
        assertThat(first.headers().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void should_omitAuthHeader_when_accessTokenIsNull() {
        PortfolioDataClient client = buildClient();
        queueStandardResponses("{\"success\":true,\"data\":null}", "{\"success\":true,\"data\":[]}", EMPTY_POSITIONS_PAGE, "{\"success\":true,\"data\":[]}");

        client.fetch(1L, "1m", null);

        ClientRequest first = capturedRequests.get(0);
        assertThat(first.headers().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void should_buildExpectedUrls_when_invoked() {
        PortfolioDataClient client = buildClient();
        queueStandardResponses("{\"success\":true,\"data\":null}", "{\"success\":true,\"data\":[]}", EMPTY_POSITIONS_PAGE, "{\"success\":true,\"data\":[]}");

        client.fetch(99L, "6m", "tok");

        assertThat(capturedRequests).hasSize(4);
        assertThat(capturedRequests.get(0).url().toString())
                .isEqualTo("http://backend:8080/api/v1/portfolios/99/view?include=summary,allocation");
        assertThat(capturedRequests.get(1).url().toString())
                .isEqualTo("http://backend:8080/api/v1/portfolios/99/allocation?mode=realizedPnl");
        assertThat(capturedRequests.get(2).url().toString())
                .isEqualTo("http://backend:8080/api/v1/portfolios/99/positions?page=0&size=100");
        assertThat(capturedRequests.get(3).url().toString())
                .isEqualTo("http://backend:8080/api/v1/portfolios/99/chart?type=performance&range=6m");
    }

    @Test
    void should_returnBundleWithEmptyCollections_when_allDataNull() {
        PortfolioDataClient client = buildClient();
        queueStandardResponses("{\"success\":true,\"data\":null}", "{\"success\":true,\"data\":null}", EMPTY_POSITIONS_PAGE, "{\"success\":true,\"data\":null}");

        PortfolioReportBundle bundle = client.fetch(1L, "1m", "tok");

        assertThat(bundle.summary()).isNull();
        assertThat(bundle.allocation()).isEmpty();
        assertThat(bundle.realizedAllocation()).isEmpty();
        assertThat(bundle.positions()).isEmpty();
        assertThat(bundle.performanceSeries()).isEmpty();
    }

    @Test
    void should_returnEmptyPerformanceSeries_when_perfDataIsNull() {
        PortfolioDataClient client = buildClient();
        queueStandardResponses(
                """
                {"success":true,"data":{
                  "summary":{"totalValueTry":1000},
                  "allocation":[]
                }}
                """,
                "{\"success\":true,\"data\":[]}",
                EMPTY_POSITIONS_PAGE,
                "{\"success\":true,\"data\":null}");

        PortfolioReportBundle bundle = client.fetch(1L, "1m", "tok");

        assertThat(bundle.performanceSeries()).isEmpty();
    }

    @Test
    void should_mapNullTotalValueToZero_when_performanceRecordHasNullValue() {
        PortfolioDataClient client = buildClient();
        queueStandardResponses(
                "{\"success\":true,\"data\":null}",
                "{\"success\":true,\"data\":[]}",
                EMPTY_POSITIONS_PAGE,
                """
                {"success":true,"data":[
                  {"timestamp":"2026-01-01T00:00:00","totalValueTry":null}
                ]}
                """);

        PortfolioReportBundle bundle = client.fetch(1L, "1m", "tok");

        assertThat(bundle.performanceSeries()).hasSize(1);
        assertThat(bundle.performanceSeries().get(0).value()).isEqualTo(0d);
    }

    @Test
    void should_iterateAllPositionPages_when_multiplePages() {
        PortfolioDataClient client = buildClient();
        queuedResponses.add(jsonResponse("{\"success\":true,\"data\":null}"));
        queuedResponses.add(jsonResponse("{\"success\":true,\"data\":[]}"));
        // page 0: 2 rows, totalPages=2
        queuedResponses.add(jsonResponse(
                "{\"success\":true,\"data\":{\"content\":[{\"id\":1,\"assetCode\":\"A\"},{\"id\":2,\"assetCode\":\"B\"}],\"page\":0,\"size\":100,\"totalElements\":3,\"totalPages\":2}}"));
        // page 1: 1 row, end of pagination
        queuedResponses.add(jsonResponse(
                "{\"success\":true,\"data\":{\"content\":[{\"id\":3,\"assetCode\":\"C\"}],\"page\":1,\"size\":100,\"totalElements\":3,\"totalPages\":2}}"));
        queuedResponses.add(jsonResponse("{\"success\":true,\"data\":[]}"));

        PortfolioReportBundle bundle = client.fetch(1L, "1m", "tok");

        assertThat(bundle.positions()).hasSize(3);
        assertThat(capturedRequests.get(2).url().toString()).endsWith("page=0&size=100");
        assertThat(capturedRequests.get(3).url().toString()).endsWith("page=1&size=100");
    }

    @Test
    void should_propagateException_when_upstreamFails() {
        PortfolioDataClient client = buildClient();
        queuedResponses.add(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                .body("boom")
                .build());

        assertThatThrownBy(() -> client.fetch(1L, "1m", "tok"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void should_constructEnvelopeRecords_when_invokedDirectly() {
        PortfolioDataClient.ApiEnvelope<String> env = new PortfolioDataClient.ApiEnvelope<>(true, "ok", "payload");
        PortfolioDataClient.PerformanceRecord rec = new PortfolioDataClient.PerformanceRecord(
                LocalDateTime.of(2026, 1, 1, 0, 0), new BigDecimal("1.23"), new BigDecimal("4.56"));
        PortfolioDataClient.ViewEnvelope view = new PortfolioDataClient.ViewEnvelope(
                new ReportSummary(BigDecimal.ONE, null, null, null, null, null, null, null, null),
                List.<ReportAllocation>of());

        assertThat(env.success()).isTrue();
        assertThat(env.data()).isEqualTo("payload");
        assertThat(rec.totalValueTry()).isEqualByComparingTo("1.23");
        assertThat(rec.pnlPercent()).isEqualByComparingTo("4.56");
        assertThat(view.allocation()).isEmpty();
    }

    @Test
    void should_trimLeadingSyntheticZeroReturns_keepingBaselineAnchor_when_seriesOpensWithZeros() {
        PortfolioDataClient client = buildClient();
        queueStandardResponses(
                "{\"success\":true,\"data\":null}",
                "{\"success\":true,\"data\":[]}",
                EMPTY_POSITIONS_PAGE,
                """
                {"success":true,"data":[
                  {"timestamp":"2026-01-01T00:00:00","totalValueTry":1000,"pnlPercent":0},
                  {"timestamp":"2026-01-02T00:00:00","totalValueTry":1000,"pnlPercent":0},
                  {"timestamp":"2026-01-03T00:00:00","totalValueTry":1000,"pnlPercent":0},
                  {"timestamp":"2026-01-04T00:00:00","totalValueTry":1100,"pnlPercent":10},
                  {"timestamp":"2026-01-05T00:00:00","totalValueTry":1200,"pnlPercent":20}
                ]}
                """);

        PortfolioReportBundle bundle = client.fetch(1L, "5Y", "tok");

        // Value series keeps every point; return series drops the flat-zero lead but keeps one 0% anchor.
        assertThat(bundle.performanceSeries()).hasSize(5);
        assertThat(bundle.returnSeries()).hasSize(3);
        assertThat(bundle.returnSeries().get(0).value()).isEqualTo(0d);
        assertThat(bundle.returnSeries().get(1).value()).isEqualTo(10d);
        assertThat(bundle.returnSeries().get(2).value()).isEqualTo(20d);
    }

    @Test
    void should_keepEntireReturnSeries_when_everyPointIsZero() {
        PortfolioDataClient client = buildClient();
        queueStandardResponses(
                "{\"success\":true,\"data\":null}",
                "{\"success\":true,\"data\":[]}",
                EMPTY_POSITIONS_PAGE,
                """
                {"success":true,"data":[
                  {"timestamp":"2026-01-01T00:00:00","totalValueTry":1000,"pnlPercent":0},
                  {"timestamp":"2026-01-02T00:00:00","totalValueTry":1000,"pnlPercent":0}
                ]}
                """);

        PortfolioReportBundle bundle = client.fetch(1L, "5Y", "tok");

        assertThat(bundle.returnSeries()).hasSize(2);
    }

    @Test
    void should_notTrimReturnSeries_when_firstPointAlreadyNonZero() {
        PortfolioDataClient client = buildClient();
        queueStandardResponses(
                "{\"success\":true,\"data\":null}",
                "{\"success\":true,\"data\":[]}",
                EMPTY_POSITIONS_PAGE,
                """
                {"success":true,"data":[
                  {"timestamp":"2026-01-01T00:00:00","totalValueTry":1000,"pnlPercent":5},
                  {"timestamp":"2026-01-02T00:00:00","totalValueTry":1100,"pnlPercent":10}
                ]}
                """);

        PortfolioReportBundle bundle = client.fetch(1L, "5Y", "tok");

        assertThat(bundle.returnSeries()).hasSize(2);
        assertThat(bundle.returnSeries().get(0).value()).isEqualTo(5d);
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
