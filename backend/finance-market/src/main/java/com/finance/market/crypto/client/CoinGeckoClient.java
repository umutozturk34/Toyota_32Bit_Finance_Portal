package com.finance.market.crypto.client;

import com.finance.market.crypto.dto.external.CoinGeckoCandleDto;
import com.finance.market.crypto.dto.external.CoinGeckoSnapshotDto;
import com.finance.market.crypto.dto.internal.BinanceKlineResponse;
import com.finance.market.crypto.dto.internal.CoinGeckoMarketDto;
import com.finance.common.exception.ExternalApiException;
import com.finance.market.crypto.mapper.CoinGeckoCandleMapper;
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
    private final String marketsPath;
    private final String klinesPath;
    private final String binanceInterval;

    public CoinGeckoClient(@Qualifier("coinGeckoWebClient") WebClient coinGeckoWebClient,
                           @Qualifier("binanceWebClient") WebClient binanceWebClient,
                           CoinGeckoCandleMapper candleMapper,
                           com.finance.common.config.AppProperties appProperties,
                           com.finance.market.crypto.config.CryptoProperties cryptoProperties) {
        this.coinGeckoWebClient = coinGeckoWebClient;
        this.binanceWebClient = binanceWebClient;
        this.candleMapper = candleMapper;
        this.marketsPath = appProperties.getApi().getCoingecko().getMarketsPath();
        this.klinesPath = appProperties.getApi().getBinance().getKlinesPath();
        this.binanceInterval = cryptoProperties.getBinanceInterval();
    }

    @CircuitBreaker(name = "coingecko")
    @Retry(name = "coingecko")
    public List<CoinGeckoSnapshotDto> fetchMarkets(String vsCurrency, List<String> coinIds) {
        try {
            List<CoinGeckoMarketDto> raw = coinGeckoWebClient.get()
                    .uri(marketsPath + "?vs_currency=" + vsCurrency
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
    public List<CoinGeckoCandleDto> fetchBinanceKlines(String coinId, String binanceSymbol,
                                                       long startTime, int limit) {
        try {
            List<BinanceKlineResponse> raw = binanceWebClient.get()
                    .uri(klinesPath + "?symbol=" + binanceSymbol
                            + "&interval=" + binanceInterval
                            + "&startTime=" + startTime
                            + "&limit=" + limit)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<BinanceKlineResponse>>() {})
                    .block();
            if (raw == null || raw.isEmpty()) return List.of();
            List<CoinGeckoCandleDto> candles = candleMapper.toCandleDtos(raw, coinId);
            log.debug("Binance klines for {} ({}): {} candles from startTime={}",
                    coinId, binanceSymbol, candles.size(), startTime);
            return candles;
        } catch (Exception e) {
            throw new ExternalApiException("Binance", "Klines fetch failed for " + coinId, e);
        }
    }
}
