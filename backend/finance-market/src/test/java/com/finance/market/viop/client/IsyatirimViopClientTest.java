package com.finance.market.viop.client;

import com.finance.common.exception.ExternalApiException;
import com.finance.market.viop.config.ViopProperties;
import com.finance.market.viop.dto.ViopContractSpec;
import com.finance.market.viop.dto.ViopHistoryPoint;
import com.finance.market.viop.dto.ViopQuoteSnapshot;
import com.finance.market.viop.dto.external.OneEndeksDto;
import com.finance.market.viop.dto.external.ViopFutureMetadataDto;
import com.finance.market.viop.dto.external.ViopOptionMetadataDto;
import com.finance.market.viop.mapper.ViopHtmlSnapshotParser;
import com.finance.market.viop.mapper.ViopMetadataMapper;
import com.finance.market.viop.mapper.ViopSnapshotMapper;
import com.finance.market.viop.model.ViopHistoryResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IsyatirimViopClientTest {

    @Mock private IsyatirimSessionManager sessionManager;
    @Mock private ViopMetadataMapper metadataMapper;
    @Mock private ViopSnapshotMapper snapshotMapper;
    @Mock private ViopHtmlSnapshotParser htmlParser;

    private ViopProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ViopProperties(null, null, null, null, null, null, null, null,
                Duration.ofMinutes(30), Duration.ofSeconds(5), null);
        when(sessionManager.currentCookieHeader()).thenReturn("X=1");
    }

    private WebClient client(ExchangeFunction exchange) {
        return WebClient.builder().baseUrl("https://example.com").exchangeFunction(exchange).build();
    }

    private IsyatirimViopClient build(WebClient webClient) {
        return new IsyatirimViopClient(webClient, sessionManager, properties,
                metadataMapper, snapshotMapper, htmlParser);
    }

    private ExchangeFunction respond(String body, MediaType type) {
        return req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", type.toString())
                .body(body)
                .build());
    }

    private ExchangeFunction respondStatus(HttpStatus status, String body) {
        return req -> Mono.just(ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body == null ? "" : body)
                .build());
    }

    @Test
    void should_throwIllegalArgument_when_fetchSnapshotWithBlankSymbol() {
        IsyatirimViopClient client = build(client(respond("[]", MediaType.APPLICATION_JSON)));

        assertThatThrownBy(() -> client.fetchSnapshot(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void should_throwIllegalArgument_when_fetchHistoryWithBlankSymbol() {
        IsyatirimViopClient client = build(client(respond("{}", MediaType.APPLICATION_JSON)));

        assertThatThrownBy(() -> client.fetchHistory(null, ViopHistoryResolution.DAILY,
                Instant.now(), Instant.now().plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throwIllegalArgument_when_fetchHistoryFromAfterTo() {
        IsyatirimViopClient client = build(client(respond("{}", MediaType.APPLICATION_JSON)));
        Instant from = Instant.now();
        Instant to = from.minusSeconds(60);

        assertThatThrownBy(() -> client.fetchHistory("F_X", ViopHistoryResolution.DAILY, from, to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid time range");
    }

    @Test
    void should_returnEmptyList_when_fetchAllLiveSnapshotsBodyBlank() {
        IsyatirimViopClient client = build(client(respond("", MediaType.TEXT_HTML)));

        List<ViopQuoteSnapshot> snaps = client.fetchAllLiveSnapshots();

        assertThat(snaps).isEmpty();
    }

    @Test
    void should_returnParsedSnapshots_when_fetchAllLiveSnapshotsReturnsHtml() {
        when(htmlParser.parse("<html></html>"))
                .thenReturn(List.of(new ViopQuoteSnapshot(
                        "F_X", Instant.now(), null, null, new BigDecimal("100"), null,
                        null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null)));
        IsyatirimViopClient client = build(client(respond("<html></html>", MediaType.TEXT_HTML)));

        List<ViopQuoteSnapshot> snaps = client.fetchAllLiveSnapshots();

        assertThat(snaps).hasSize(1);
    }

    @Test
    void should_throwExternalApiException_when_fetchAllLiveSnapshots4xx() {
        IsyatirimViopClient client = build(client(respondStatus(HttpStatus.SERVICE_UNAVAILABLE, "")));

        assertThatThrownBy(client::fetchAllLiveSnapshots)
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void should_returnEmptyList_when_fetchFutureContractSpecsReturnsNullValue() {
        IsyatirimViopClient client = build(client(respond("{\"value\":null}", MediaType.APPLICATION_JSON)));

        List<ViopContractSpec> specs = client.fetchFutureContractSpecs();

        assertThat(specs).isEmpty();
    }

    @Test
    void should_mapFutureSpecs_when_fetchFutureContractSpecsReturnsValues() {
        when(metadataMapper.toFutureSpec(any(ViopFutureMetadataDto.class)))
                .thenReturn(ViopContractSpec.future("F_X", "X", "X",
                        java.time.LocalDate.of(2026, 6, 30), new BigDecimal("1000"),
                        new BigDecimal("3000"), "Nakdi", "TRY"));
        String body = "{\"value\":[{\"sembol\":\"F_USDTRY0626\"}]}";
        IsyatirimViopClient client = build(client(respond(body, MediaType.APPLICATION_JSON)));

        List<ViopContractSpec> specs = client.fetchFutureContractSpecs();

        assertThat(specs).hasSize(1);
        verify(metadataMapper).toFutureSpec(any(ViopFutureMetadataDto.class));
    }

    @Test
    void should_returnEmptyList_when_fetchOptionContractTemplatesReturnsNullValue() {
        IsyatirimViopClient client = build(client(respond("{\"value\":null}", MediaType.APPLICATION_JSON)));

        List<ViopContractSpec> specs = client.fetchOptionContractTemplates();

        assertThat(specs).isEmpty();
    }

    @Test
    void should_mapOptionTemplateSpecs_when_fetchOptionContractTemplatesReturnsValues() {
        when(metadataMapper.toOptionTemplateSpec(any(ViopOptionMetadataDto.class)))
                .thenReturn(ViopContractSpec.future("O_X", "X", "X",
                        java.time.LocalDate.of(2026, 6, 30), new BigDecimal("100"),
                        new BigDecimal("300"), "Nakdi", "TRY"));
        String body = "{\"value\":[{\"sembol\":\"O_X\"}]}";
        IsyatirimViopClient client = build(client(respond(body, MediaType.APPLICATION_JSON)));

        List<ViopContractSpec> specs = client.fetchOptionContractTemplates();

        assertThat(specs).hasSize(1);
    }

    @Test
    void should_throwExternalApiException_when_fetchSnapshotReturnsEmptyArray() {
        IsyatirimViopClient client = build(client(respond("[]", MediaType.APPLICATION_JSON)));

        assertThatThrownBy(() -> client.fetchSnapshot("F_X"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("empty payload");
    }

    @Test
    void should_returnMappedSnapshot_when_fetchSnapshotReturnsValues() {
        when(snapshotMapper.fromOneEndeks(any(OneEndeksDto.class)))
                .thenReturn(new ViopQuoteSnapshot("F_X", Instant.now(),
                        null, null, new BigDecimal("35.15"), null,
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null));
        String body = "[{\"symbol\":\"F_X\",\"last\":35.15}]";
        IsyatirimViopClient client = build(client(respond(body, MediaType.APPLICATION_JSON)));

        ViopQuoteSnapshot snap = client.fetchSnapshot("F_X");

        assertThat(snap.symbol()).isEqualTo("F_X");
    }

    @Test
    void should_retryWithRefreshedSession_when_initial401() {
        AtomicInteger callCount = new AtomicInteger();
        ExchangeFunction firstAuthFailThenSuccess = req -> {
            int n = callCount.incrementAndGet();
            if (n == 1) {
                return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body("unauthorized")
                        .build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("[{\"symbol\":\"F_X\"}]")
                    .build());
        };
        when(snapshotMapper.fromOneEndeks(any(OneEndeksDto.class)))
                .thenReturn(new ViopQuoteSnapshot("F_X", Instant.now(),
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null));
        IsyatirimViopClient client = build(client(firstAuthFailThenSuccess));

        ViopQuoteSnapshot snap = client.fetchSnapshot("F_X");

        assertThat(snap.symbol()).isEqualTo("F_X");
        verify(sessionManager).forceRefresh();
    }

    @Test
    void should_throwExternalApiException_when_retryAlsoFails() {
        ExchangeFunction always401 = req -> Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("unauthorized")
                .build());
        IsyatirimViopClient client = build(client(always401));

        assertThatThrownBy(() -> client.fetchSnapshot("F_X"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void should_throwExternalApiException_when_genericExceptionOccurs() {
        ExchangeFunction failing = req -> Mono.error(new IllegalStateException("boom"));
        IsyatirimViopClient client = build(client(failing));

        assertThatThrownBy(() -> client.fetchSnapshot("F_X"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void should_throwExternalApiException_when_non401Status() {
        IsyatirimViopClient client = build(client(respondStatus(HttpStatus.INTERNAL_SERVER_ERROR, "{}")));

        assertThatThrownBy(() -> client.fetchSnapshot("F_X"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void should_returnEmptyHistory_when_chartResponseDataIsNull() {
        IsyatirimViopClient client = build(client(respond("{\"data\":null}", MediaType.APPLICATION_JSON)));

        List<ViopHistoryPoint> points = client.fetchHistory("F_X", ViopHistoryResolution.DAILY,
                Instant.now().minusSeconds(86400), Instant.now());

        assertThat(points).isEmpty();
    }

    @Test
    void should_returnEmptyHistory_when_chartResponseDataIsEmpty() {
        IsyatirimViopClient client = build(client(respond("{\"data\":[]}", MediaType.APPLICATION_JSON)));

        List<ViopHistoryPoint> points = client.fetchHistory("F_X", ViopHistoryResolution.DAILY,
                Instant.now().minusSeconds(86400), Instant.now());

        assertThat(points).isEmpty();
    }

    @Test
    void should_parseChartPoints_when_chartResponseHasData() {
        String body = "{\"data\":[[1714579200000,35.15],[1714665600000,35.25]]}";
        IsyatirimViopClient client = build(client(respond(body, MediaType.APPLICATION_JSON)));

        List<ViopHistoryPoint> points = client.fetchHistory("F_X", ViopHistoryResolution.DAILY,
                Instant.now().minusSeconds(86400), Instant.now());

        assertThat(points).hasSize(2);
        assertThat(points.get(0).close()).isEqualByComparingTo("35.15");
    }

    @Test
    void should_skipInvalidEntry_when_chartResponseHasShortEntries() {
        String body = "{\"data\":[[1714579200000],[1714665600000,35.25]]}";
        IsyatirimViopClient client = build(client(respond(body, MediaType.APPLICATION_JSON)));

        List<ViopHistoryPoint> points = client.fetchHistory("F_X", ViopHistoryResolution.DAILY,
                Instant.now().minusSeconds(86400), Instant.now());

        assertThat(points).hasSize(1);
        assertThat(points.get(0).close()).isEqualByComparingTo("35.25");
    }
}
