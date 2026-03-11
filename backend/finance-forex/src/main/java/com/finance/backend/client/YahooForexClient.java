package com.finance.backend.client;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.dto.internal.YahooChartResponse.Result;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.ForexMapper;
import com.finance.backend.mapper.YahooCandleMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.time.ZoneId;
@Log4j2
@Component
public class YahooForexClient extends AbstractYahooClient {
    private final ForexMapper forexMapper;
    public YahooForexClient(@Qualifier("yahooRestTemplate") RestTemplate restTemplate,
                            @Value("${app.api.yahoo.base-url}") String baseUrl,
                            ForexMapper forexMapper,
                            YahooCandleMapper candleMapper) {
        super(restTemplate, baseUrl, ZoneId.systemDefault(), false, candleMapper);
        this.forexMapper = forexMapper;
    }
    public YahooQuoteDto fetchQuote(String yahooSymbol) {
        try {
            Result result = fetchChart(yahooSymbol, "1d", "1m");
            YahooQuoteDto quote = forexMapper.toQuoteDto(result.meta());
            log.debug("Fetched quote for {}: {}", yahooSymbol, quote.regularMarketPrice());
            return quote;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Quote fetch failed for {}", yahooSymbol, e);
            throw new ExternalApiException("Yahoo Finance", "Quote fetch failed for " + yahooSymbol, e);
        }
    }
}
