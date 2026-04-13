package com.finance.backend.client;

import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.internal.YahooChartResponse;
import com.finance.backend.dto.internal.YahooChartResponse.Result;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.exception.SymbolNotFoundException;
import com.finance.backend.mapper.YahooClientMapper;
import com.finance.backend.config.AppProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

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

    protected Result fetchChart(String symbol, String range, String interval) {
        YahooChartResponse response;
        try {
            response = webClient.get()
                    .uri(chartPath + symbol + "?range=" + range + "&interval=" + interval)
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
}
