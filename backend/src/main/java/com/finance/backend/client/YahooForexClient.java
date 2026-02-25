package com.finance.backend.client;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooQuoteDto;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
@Component
@Slf4j
public class YahooForexClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;
    public YahooForexClient(@Qualifier("yahooRestTemplate") RestTemplate restTemplate,
                            @Value("${app.api.yahoo.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
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
    public List<YahooCandleDto> fetchCandles(String yahooSymbol, String range, String interval) {
        try {
            Result result = fetchChart(yahooSymbol, range, interval);
            Quote quote = result.firstQuote();
            if (result.timestamp() == null || result.timestamp().isEmpty() || quote == null) {
                return List.of();
            }
            List<YahooCandleDto> candles = new ArrayList<>(result.timestamp().size());
            for (int i = 0; i < result.timestamp().size(); i++) {
                if (!quote.isValidAt(i)) continue;
                LocalDateTime date = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(result.timestamp().get(i)), ZoneId.systemDefault());
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
            throw new ExternalApiException("Yahoo Finance", "Candle fetch failed for " + yahooSymbol, e);
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
