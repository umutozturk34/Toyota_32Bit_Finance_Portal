package com.finance.commodity.client;
import com.finance.common.client.AbstractYahooClient;


import com.finance.common.config.AppProperties;
import com.finance.common.dto.external.YahooQuoteDto;
import com.finance.common.dto.internal.YahooChartResponse.Result;
import com.finance.common.mapper.YahooClientMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Log4j2
@Component
public class YahooCommodityClient extends AbstractYahooClient {

    public YahooCommodityClient(@Qualifier("yahooWebClient") WebClient webClient,
                                YahooClientMapper yahooClientMapper,
                                AppProperties appProperties) {
        super(webClient, yahooClientMapper, appProperties);
    }

    @CircuitBreaker(name = "yahoo")
    @Retry(name = "yahoo")
    public YahooQuoteDto fetchQuote(String symbol) {
        log.debug("Fetching commodity quote for {}", symbol);
        Result result = fetchChart(symbol, "1d", "1m");
        return yahooClientMapper.toQuoteDto(result);
    }
}
