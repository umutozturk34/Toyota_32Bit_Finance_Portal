package com.finance.backend.client;
import com.finance.backend.dto.external.YahooStockQuoteDto;
import com.finance.backend.dto.internal.YahooChartResponse.Meta;
import com.finance.backend.dto.internal.YahooChartResponse.Quote;
import com.finance.backend.dto.internal.YahooChartResponse.Result;
import com.finance.backend.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Objects;
@Component
@Slf4j
public class YahooStockClient extends AbstractYahooClient {
    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    public YahooStockClient(@Qualifier("yahooRestTemplate") RestTemplate restTemplate,
                            @Value("${app.api.yahoo.base-url}") String baseUrl) {
        super(restTemplate, baseUrl, ISTANBUL_ZONE, true);
    }
    public YahooStockQuoteDto fetchSnapshot(String symbol) {
        try {
            Result result = fetchChart(symbol, "1d", "1d");
            Meta meta = result.meta();
            Quote quote = result.firstQuote();
            BigDecimal openPrice = (quote != null && quote.open() != null && !quote.open().isEmpty())
                    ? quote.open().getFirst() : null;
            return new YahooStockQuoteDto(
                    symbol,
                    Objects.toString(meta.longName(), Objects.toString(meta.shortName(), symbol)),
                    meta.regularMarketPrice(),
                    meta.chartPreviousClose(),
                    openPrice,
                    meta.dayHigh(),
                    meta.dayLow(),
                    meta.volume() != null ? meta.volume() : 0L,
                    Objects.toString(meta.exchangeName(), "BIST"),
                    Objects.toString(meta.currency(), "TRY")
            );
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Yahoo Finance", "Snapshot fetch failed for " + symbol, e);
        }
    }
}
