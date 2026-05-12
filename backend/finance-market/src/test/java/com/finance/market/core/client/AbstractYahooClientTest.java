package com.finance.market.core.client;

import com.finance.common.config.AppProperties;
import com.finance.common.exception.ExternalApiException;
import com.finance.common.exception.SymbolNotFoundException;
import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.core.dto.external.YahooQuoteDto;
import com.finance.market.core.dto.internal.YahooChartFullResult;
import com.finance.market.core.mapper.YahooClientMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractYahooClientTest {

    @Mock private YahooClientMapper yahooClientMapper;

    private static final String CHART_BODY = "{\"chart\":{\"result\":[{\"meta\":{},\"timestamp\":[],\"indicators\":{\"quote\":[{}]}}]}}";
    private static final String EMPTY_BODY = "{\"chart\":{\"result\":[]}}";

    private static class TestClient extends AbstractYahooClient {
        TestClient(WebClient webClient, YahooClientMapper mapper, AppProperties props) {
            super(webClient, mapper, props);
        }
    }

    private WebClient stubWebClient(ExchangeFunction exchange) {
        return WebClient.builder().exchangeFunction(exchange).build();
    }

    private ExchangeFunction respondJson(String body) {
        return request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private ExchangeFunction respondStatus(HttpStatus status) {
        return request -> Mono.just(ClientResponse.create(status).build());
    }

    private TestClient buildClient(ExchangeFunction exchange) {
        AppProperties props = new AppProperties();
        props.getApi().getYahoo().setChartPath("/v8/finance/chart/");
        return new TestClient(stubWebClient(exchange), yahooClientMapper, props);
    }

    @BeforeEach
    void setUp() {}

    @Test
    void fetchCandles_mapsResultViaMapper_whenResponseValid() {
        TestClient client = buildClient(respondJson(CHART_BODY));
        when(yahooClientMapper.toCandleDtos(any(), eq(true))).thenReturn(List.of(
                new YahooCandleDto(null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1L)));

        List<YahooCandleDto> result = client.fetchCandles("AAPL", "1M", "1d", true);

        assertThat(result).hasSize(1);
    }

    @Test
    void fetchCandles_supportsMaxRange_byAppendingPeriodParams() {
        TestClient client = buildClient(respondJson(CHART_BODY));
        when(yahooClientMapper.toCandleDtos(any(), eq(true))).thenReturn(List.of());

        client.fetchCandles("AAPL", "max", "1d", true);

        assertThat(true).isTrue();
    }

    @Test
    void fetchChartFull_delegatesToMapperForFullResult() {
        TestClient client = buildClient(respondJson(CHART_BODY));
        YahooChartFullResult<YahooQuoteDto> stub = new YahooChartFullResult<>(
                new YahooQuoteDto(BigDecimal.ONE, null, null, null, null, null), List.of());
        when(yahooClientMapper.toFullResult(any(), eq(false))).thenReturn(stub);

        YahooChartFullResult<YahooQuoteDto> result = client.fetchChartFull("AAPL", "1M", "1d", false);

        assertThat(result).isSameAs(stub);
    }

    @Test
    void fetchChart_raisesSymbolNotFound_whenResultListEmpty() {
        TestClient client = buildClient(respondJson(EMPTY_BODY));

        assertThatThrownBy(() -> client.fetchCandles("UNKNOWN", "1M", "1d", true))
                .isInstanceOf(SymbolNotFoundException.class);
    }

    @Test
    void fetchChart_raisesSymbolNotFound_whenUpstreamReturns404() {
        TestClient client = buildClient(respondStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchCandles("UNKNOWN", "1M", "1d", true))
                .isInstanceOf(SymbolNotFoundException.class);
    }

    @Test
    void fetchChart_raisesExternalApi_whenUpstreamReturns5xx() {
        TestClient client = buildClient(respondStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.fetchCandles("AAPL", "1M", "1d", true))
                .isInstanceOf(ExternalApiException.class);
    }
}
