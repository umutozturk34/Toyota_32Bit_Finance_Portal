package com.finance.backend.client;

import com.finance.backend.dto.external.CoinGeckoCandleDto;
import com.finance.backend.dto.external.CoinGeckoSnapshotDto;
import com.finance.backend.dto.internal.CoinGeckoMarketChartResponse;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Log4j2
public class CoinGeckoClient {

    private final WebClient webClient;
    private final CoinGeckoCandleMapper candleMapper;

    public CoinGeckoClient(@Qualifier("coinGeckoWebClient") WebClient webClient,
                           CoinGeckoCandleMapper candleMapper) {
        this.webClient = webClient;
        this.candleMapper = candleMapper;
    }

    @CircuitBreaker(name = "coingecko")
    @Retry(name = "coingecko")
    public List<CoinGeckoSnapshotDto> fetchMarkets(String vsCurrency, List<String> coinIds) {
        try {
            List<CoinGeckoMarketDto> raw = webClient.get()
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

    @CircuitBreaker(name = "coingecko")
    @Retry(name = "coingecko")
    public List<CoinGeckoCandleDto> fetchMarketChartRange(String coinId, int days) {
        try {
            long toTimestamp = Instant.now().getEpochSecond();
            long fromTimestamp = Instant.now().minus(days, ChronoUnit.DAYS).getEpochSecond();
            CoinGeckoMarketChartResponse chart = webClient.get()
                    .uri("/coins/" + coinId + "/market_chart/range?vs_currency=usd"
                            + "&from=" + fromTimestamp + "&to=" + toTimestamp)
                    .retrieve()
                    .bodyToMono(CoinGeckoMarketChartResponse.class)
                    .block();
            List<CoinGeckoCandleDto> candles = candleMapper.toCandleDtosFromChart(chart, coinId);
            log.debug("CoinGecko chart range for {} ({} days): {} candles", coinId, days, candles.size());
            return candles;
        } catch (Exception e) {
            throw new ExternalApiException("CoinGecko", "Chart fetch failed for " + coinId, e);
        }
    }

    @CircuitBreaker(name = "coingecko")
    @Retry(name = "coingecko")
    public List<CoinGeckoCandleDto> fetchDailyOhlc(String coinId) {
        try {
            List<List<BigDecimal>> ohlcData = webClient.get()
                    .uri("/coins/" + coinId + "/ohlc?vs_currency=usd&days=1")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<List<BigDecimal>>>() {})
                    .block();
            List<CoinGeckoCandleDto> candles = candleMapper.toCandleDtosFromOhlc(ohlcData, coinId);
            log.debug("CoinGecko OHLC for {}: {} candles", coinId, candles.size());
            return candles;
        } catch (Exception e) {
            throw new ExternalApiException("CoinGecko", "OHLC fetch failed for " + coinId, e);
        }
    }
}
