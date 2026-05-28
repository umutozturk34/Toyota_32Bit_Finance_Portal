package com.finance.market.stock.client;
import com.finance.market.core.client.AbstractYahooClient;


import com.finance.common.config.AppProperties;
import com.finance.market.stock.dto.external.YahooStockQuoteDto;
import com.finance.market.core.dto.internal.YahooChartFullResult;
import com.finance.market.core.dto.internal.YahooChartResponse.Result;
import com.finance.market.core.mapper.YahooClientMapper;
import com.finance.market.stock.mapper.YahooStockQuoteMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** Yahoo client for BIST stocks: fetches a quote or a full quote+candles chart for a symbol. */
@Log4j2
@Component
public class YahooStockClient extends AbstractYahooClient {

    private final YahooStockQuoteMapper yahooStockQuoteMapper;

    public YahooStockClient(@Qualifier("yahooWebClient") WebClient webClient,
                            YahooClientMapper yahooClientMapper,
                            YahooStockQuoteMapper yahooStockQuoteMapper,
                            AppProperties appProperties) {
        super(webClient, yahooClientMapper, appProperties);
        this.yahooStockQuoteMapper = yahooStockQuoteMapper;
    }

    @CircuitBreaker(name = "yahoo")
    @Retry(name = "yahoo")
    public YahooStockQuoteDto fetchQuote(String symbol) {
        log.debug("Fetching stock quote for {}", symbol);
        Result result = fetchChart(symbol, "1d", "1d");
        return yahooStockQuoteMapper.toDto(result, symbol);
    }

    @CircuitBreaker(name = "yahoo")
    @Retry(name = "yahoo")
    public YahooChartFullResult<YahooStockQuoteDto> fetchStockChartFull(
            String symbol, String range, String interval, boolean truncateToDays) {
        log.debug("Fetching stock chart (quote+candles): symbol={}, range={}, interval={}", symbol, range, interval);
        Result result = fetchChart(symbol, range, interval);
        return new YahooChartFullResult<>(
                yahooStockQuoteMapper.toDto(result, symbol),
                yahooClientMapper.toCandleDtos(result, truncateToDays));
    }
}
