package com.finance.backend.client;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.YahooStockQuoteDto;
import com.finance.backend.dto.internal.YahooChartResponse.Result;
import com.finance.backend.mapper.YahooClientMapper;
import com.finance.backend.mapper.YahooStockQuoteMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

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
}
