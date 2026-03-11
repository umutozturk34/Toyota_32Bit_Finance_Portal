package com.finance.backend.client;

import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.dto.internal.YahooChartResponse.Result;
import com.finance.backend.mapper.YahooClientMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Log4j2
@Component
public class YahooForexClient extends AbstractYahooClient {

    public YahooForexClient(@Qualifier("yahooWebClient") WebClient webClient,
                            YahooClientMapper yahooClientMapper) {
        super(webClient, yahooClientMapper);
    }

    @CircuitBreaker(name = "yahoo")
    @Retry(name = "yahoo")
    public YahooQuoteDto fetchQuote(String symbol) {
        log.debug("Fetching forex quote for {}", symbol);
        Result result = fetchChart(symbol, "1d", "1m");
        return yahooClientMapper.toQuoteDto(result);
    }
}
