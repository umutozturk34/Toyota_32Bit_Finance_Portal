package com.finance.backend.client;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.dto.internal.YahooChartResponse.Meta;
import com.finance.backend.dto.internal.YahooChartResponse.Result;
import com.finance.backend.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.time.ZoneId;
@Component
@Slf4j
public class YahooForexClient extends AbstractYahooClient {
    public YahooForexClient(@Qualifier("yahooRestTemplate") RestTemplate restTemplate,
                            @Value("${app.api.yahoo.base-url}") String baseUrl) {
        super(restTemplate, baseUrl, ZoneId.systemDefault(), false);
    }
    public YahooQuoteDto fetchQuote(String yahooSymbol) {
        try {
            Result result = fetchChart(yahooSymbol, "1d", "1m");
            Meta meta = result.meta();
            return new YahooQuoteDto(meta.regularMarketPrice(), meta.previousClose());
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Yahoo Finance", "Quote fetch failed for " + yahooSymbol, e);
        }
    }
}
