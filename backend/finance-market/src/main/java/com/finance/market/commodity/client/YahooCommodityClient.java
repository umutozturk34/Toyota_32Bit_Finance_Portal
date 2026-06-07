package com.finance.market.commodity.client;
import com.finance.market.core.client.AbstractYahooClient;


import com.finance.common.config.AppProperties;
import com.finance.market.core.dto.external.YahooQuoteDto;
import com.finance.market.core.dto.internal.YahooChartResponse.Result;
import com.finance.market.core.mapper.YahooClientMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Yahoo Finance client specialized for commodity instruments (e.g. gold,
 * silver, platinum, palladium futures).
 *
 * <p>Reuses the shared Yahoo chart fetching/mapping pipeline provided by
 * {@link AbstractYahooClient} and exposes a single latest-quote lookup. Calls
 * are guarded by Resilience4j circuit breaker and retry under the {@code yahoo}
 * instance name.
 */
@Log4j2
@Component
public class YahooCommodityClient extends AbstractYahooClient {

    /**
     * Creates the commodity client bound to the rate-limited Yahoo WebClient.
     *
     * @param webClient         the qualified {@code yahooWebClient} bean
     * @param yahooClientMapper maps raw Yahoo chart results to internal DTOs
     * @param appProperties     application-wide external API configuration
     */
    public YahooCommodityClient(@Qualifier("yahooWebClient") WebClient webClient,
                                YahooClientMapper yahooClientMapper,
                                AppProperties appProperties) {
        super(webClient, yahooClientMapper, appProperties);
    }

    /**
     * Fetches the latest quote for a single commodity symbol using a one-day
     * range at one-minute granularity, then maps it to a {@link YahooQuoteDto}.
     *
     * @param symbol the Yahoo Finance ticker (e.g. {@code GC=F} for gold)
     * @return the latest quote payload for the symbol
     */
    @CircuitBreaker(name = "yahoo")
    @Retry(name = "yahoo")
    public YahooQuoteDto fetchQuote(String symbol) {
        log.debug("Fetching commodity quote for {}", symbol);
        Result result = fetchChart(symbol, "1d", "1m");
        return yahooClientMapper.toQuoteDto(result);
    }
}
