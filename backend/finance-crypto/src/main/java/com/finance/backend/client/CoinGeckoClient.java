package com.finance.backend.client;

import com.finance.backend.dto.external.CoinGeckoCandleDto;
import com.finance.backend.dto.external.CoinGeckoSnapshotDto;
import com.finance.backend.dto.internal.BinanceKlineResponse;
import com.finance.backend.dto.internal.CoinGeckoMarketDto;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.CoinGeckoCandleMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
@Log4j2
public class CoinGeckoClient {

    private final WebClient coinGeckoWebClient;
    private final WebClient binanceWebClient;
    private final CoinGeckoCandleMapper candleMapper;

    public CoinGeckoClient(@Qualifier("coinGeckoWebClient") WebClient coinGeckoWebClient,
                           @Qualifier("binanceWebClient") WebClient binanceWebClient,
                           CoinGeckoCandleMapper candleMapper) {
        this.coinGeckoWebClient = coinGeckoWebClient;
        this.binanceWebClient = binanceWebClient;
        this.candleMapper = candleMapper;
    }

    @CircuitBreaker(name = "coingecko")
    @Retry(name = "coingecko")
    public List<CoinGeckoSnapshotDto> fetchMarkets(String vsCurrency, List<String> coinIds) {
        try {
            List<CoinGeckoMarketDto> raw = coinGeckoWebClient.get()
                    .uri("/coins/markets?vs_currency=" + vsCurrency
                            + "&ids=" + String.join(",", coinIds)
                            + "&order=market_cap_desc&sparkline=false")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<CoinGeckoMarketDto>>() {})
                    .block();
            List<CoinGeckoSnapshotDto> snapshots = candleMapper.toSnapshotDtos(raw);
            log.debug("CoinGecko markets ({}): {} coins fetched", vsCurrency, snapshots.size());
            return snapshots;
        } catch (Exception e) {
            throw new ExternalApiException("CoinGecko", "Market fetch failed for " + vsCurrency, e);
        }
    }

    @CircuitBreaker(name = "binance")
    @Retry(name = "binance")
    public List<CoinGeckoCandleDto> fetchBinanceKlines(String coinId, String binanceSymbol, int days) {
        try {
            List<BinanceKlineResponse> raw = binanceWebClient.get()
                    .uri("/api/v3/klines?symbol=" + binanceSymbol
                            + "&interval=1d&limit=" + days)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<BinanceKlineResponse>>() {})
                    .block();
            if (raw == null || raw.isEmpty()) return List.of();
            List<CoinGeckoCandleDto> candles = candleMapper.toCandleDtos(raw, coinId);
            log.debug("Binance klines for {} ({}): {} candles", coinId, binanceSymbol, candles.size());
            return candles;
        } catch (Exception e) {
            throw new ExternalApiException("Binance", "Klines fetch failed for " + coinId, e);
        }
    }
}
