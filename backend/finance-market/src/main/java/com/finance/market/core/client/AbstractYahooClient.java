package com.finance.market.core.client;

import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.core.dto.external.YahooQuoteDto;
import com.finance.market.core.dto.internal.YahooChartFullResult;
import com.finance.market.core.dto.internal.YahooChartResponse;
import com.finance.market.core.dto.internal.YahooChartResponse.Result;
import com.finance.common.exception.ExternalApiException;
import com.finance.common.exception.SymbolNotFoundException;
import com.finance.market.core.mapper.YahooClientMapper;
import com.finance.common.config.AppProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

/**
 * Base Yahoo Finance chart WebClient: fetches candles (and optionally the quote) for a symbol over
 * a range/interval, guarded by circuit-breaker/retry. A 404 maps to {@link SymbolNotFoundException};
 * the {@code max} range is translated to a full {@code period1=0..now} query.
 */
@Log4j2
public abstract class AbstractYahooClient {

    protected final WebClient webClient;
    protected final YahooClientMapper yahooClientMapper;
    protected final String chartPath;

    protected AbstractYahooClient(WebClient webClient, YahooClientMapper yahooClientMapper, AppProperties appProperties) {
        this.webClient = webClient;
        this.yahooClientMapper = yahooClientMapper;
        this.chartPath = appProperties.getApi().getYahoo().getChartPath();
    }

    @CircuitBreaker(name = "yahoo")
    @Retry(name = "yahoo")
    public List<YahooCandleDto> fetchCandles(String symbol, String range, String interval, boolean truncateToDays) {
        log.debug("Fetching candles: symbol={}, range={}, interval={}", symbol, range, interval);
        Result result = fetchChart(symbol, range, interval);
        List<YahooCandleDto> candles = yahooClientMapper.toCandleDtos(result, truncateToDays);
        log.debug("Yahoo returned {} candles for {}", candles.size(), symbol);
        return candles;
    }

    @CircuitBreaker(name = "yahoo")
    @Retry(name = "yahoo")
    public YahooChartFullResult<YahooQuoteDto> fetchChartFull(
            String symbol, String range, String interval, boolean truncateToDays) {
        log.debug("Fetching chart (quote+candles): symbol={}, range={}, interval={}", symbol, range, interval);
        Result result = fetchChart(symbol, range, interval);
        return yahooClientMapper.toFullResult(result, truncateToDays);
    }

    /** Fetches the first chart result, throwing {@link SymbolNotFoundException} when the symbol has no data. */
    protected Result fetchChart(String symbol, String range, String interval) {
        YahooChartResponse response;
        try {
            response = webClient.get()
                    .uri(chartPath + symbol + "?" + buildRangeQuery(range) + "&interval=" + interval)
                    .retrieve()
                    .bodyToMono(YahooChartResponse.class)
                    .block();
        } catch (WebClientResponseException.NotFound e) {
            throw new SymbolNotFoundException(symbol);
        } catch (Exception e) {
            throw new ExternalApiException("Yahoo Finance", "Request failed for " + symbol, e);
        }
        if (response == null || response.chart() == null || response.chart().result() == null
                || response.chart().result().isEmpty()) {
            throw new SymbolNotFoundException(symbol);
        }
        return response.chart().result().getFirst();
    }

    private String buildRangeQuery(String range) {
        if ("max".equalsIgnoreCase(range)) {
            return "period1=0&period2=" + (System.currentTimeMillis() / 1000L);
        }
        return "range=" + range;
    }
}
