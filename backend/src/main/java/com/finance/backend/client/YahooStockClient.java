package com.finance.backend.client;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooStockQuoteDto;
import com.finance.backend.dto.internal.YahooChartResponse;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
@Component
@Slf4j
public class YahooStockClient {
    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    private final RestTemplate restTemplate;
    private final String baseUrl;
    public YahooStockClient(@Qualifier("yahooRestTemplate") RestTemplate restTemplate,
                            @Value("${app.api.yahoo.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
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
    public List<YahooCandleDto> fetchCandles(String symbol, String range, String interval) {
        try {
            Result result = fetchChart(symbol, range, interval);
            Quote quote = result.firstQuote();
            if (result.timestamp() == null || result.timestamp().isEmpty() || quote == null) {
                return List.of();
            }
            List<YahooCandleDto> candles = new ArrayList<>(result.timestamp().size());
            for (int i = 0; i < result.timestamp().size(); i++) {
                if (!quote.isValidAt(i)) continue;
                LocalDateTime date = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(result.timestamp().get(i)), ISTANBUL_ZONE)
                        .truncatedTo(ChronoUnit.DAYS);
                Long vol = (quote.volume() != null && i < quote.volume().size())
                        ? quote.volume().get(i) : null;
                candles.add(new YahooCandleDto(date,
                        quote.open().get(i), quote.high().get(i),
                        quote.low().get(i), quote.close().get(i), vol));
            }
            return candles;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Yahoo Finance", "Candle fetch failed for " + symbol, e);
        }
    }
    private Result fetchChart(String symbol, String range, String interval) {
        String url = baseUrl + symbol + "?range=" + range + "&interval=" + interval;
        try {
            YahooChartResponse response = restTemplate.getForObject(url, YahooChartResponse.class);
            if (response == null || response.chart() == null) {
                throw new ExternalApiException("Yahoo Finance", "Empty response for " + symbol);
            }
            Result result = response.chart().firstResult();
            if (result == null) {
                throw new ExternalApiException("Yahoo Finance", "No chart data for " + symbol);
            }
            return result;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Yahoo Finance", "Request failed: " + url, e);
        }
    }
}
