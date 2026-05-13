package com.finance.market.crypto.client;

import com.finance.common.config.AppProperties;
import com.finance.common.exception.ExternalApiException;
import com.finance.market.crypto.config.CryptoProperties;
import com.finance.market.crypto.dto.external.CoinGeckoCandleDto;
import com.finance.market.crypto.dto.external.CoinGeckoSnapshotDto;
import com.finance.market.crypto.mapper.CoinGeckoCandleMapper;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoinGeckoClientTest {

    @Mock private CoinGeckoCandleMapper candleMapper;

    private CoinGeckoClient client;

    private WebClient stubWebClient(ExchangeFunction exchange) {
        return WebClient.builder().exchangeFunction(exchange).build();
    }

    private ExchangeFunction respondJson(String body) {
        return request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private ExchangeFunction respondError() {
        return request -> Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }

    private CoinGeckoClient buildClient(WebClient cg, WebClient bn) {
        AppProperties appProperties = new AppProperties();
        appProperties.getApi().getCoingecko().setMarketsPath("/coins/markets");
        appProperties.getApi().getBinance().setKlinesPath("/api/v3/klines");
        CryptoProperties cryptoProperties = new CryptoProperties();
        cryptoProperties.setBinanceInterval("1d");
        return new CoinGeckoClient(cg, bn, candleMapper, appProperties, cryptoProperties);
    }

    @BeforeEach
    void setUp() {
        client = buildClient(stubWebClient(respondJson("[]")), stubWebClient(respondJson("[]")));
    }

    @Test
    void fetchMarkets_returnsMappedSnapshots_whenCoinGeckoRespondsOk() {
        client = buildClient(
                stubWebClient(respondJson("[{\"id\":\"bitcoin\",\"symbol\":\"BTC\",\"name\":\"Bitcoin\",\"current_price\":60000}]")),
                stubWebClient(respondJson("[]")));
        when(candleMapper.toSnapshotDtos(any())).thenReturn(List.of(
                new CoinGeckoSnapshotDto("bitcoin", "BTC", "Bitcoin", null,
                        new BigDecimal("60000"), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO)));

        List<CoinGeckoSnapshotDto> result = client.fetchMarkets("usd", List.of("bitcoin"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("bitcoin");
    }

    @Test
    void fetchMarkets_raises_whenUpstreamReturnsError() {
        client = buildClient(stubWebClient(respondError()), stubWebClient(respondJson("[]")));

        assertThatThrownBy(() -> client.fetchMarkets("usd", List.of("bitcoin")))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Market fetch failed");
    }

    @Test
    void fetchBinanceKlines_returnsCandles_whenBinanceRespondsOk() {
        String kline = "[[1715472000000,\"60000\",\"61000\",\"59000\",\"60500\",\"100\"]]";
        client = buildClient(stubWebClient(respondJson("[]")),
                stubWebClient(respondJson(kline)));
        when(candleMapper.toCandleDtos(any(), anyString())).thenReturn(List.of(
                new CoinGeckoCandleDto("bitcoin",
                        LocalDateTime.of(2026, 5, 12, 0, 0),
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1L)));

        List<CoinGeckoCandleDto> result = client.fetchBinanceKlines("bitcoin", "BTCUSDT", 0L, 100);

        assertThat(result).hasSize(1);
    }

    @Test
    void fetchBinanceKlines_returnsEmptyList_whenRawIsEmpty() {
        client = buildClient(stubWebClient(respondJson("[]")), stubWebClient(respondJson("[]")));

        List<CoinGeckoCandleDto> result = client.fetchBinanceKlines("bitcoin", "BTCUSDT", 0L, 100);

        assertThat(result).isEmpty();
    }

    @Test
    void fetchBinanceKlines_raises_whenUpstreamReturnsError() {
        client = buildClient(stubWebClient(respondJson("[]")), stubWebClient(respondError()));

        assertThatThrownBy(() -> client.fetchBinanceKlines("bitcoin", "BTCUSDT", 0L, 100))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Klines fetch failed");
    }
}
