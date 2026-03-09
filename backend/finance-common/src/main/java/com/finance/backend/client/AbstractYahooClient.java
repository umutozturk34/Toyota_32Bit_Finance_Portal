package com.finance.backend.client;

import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.internal.YahooChartResponse;
import com.finance.backend.dto.internal.YahooChartResponse.Quote;
import com.finance.backend.dto.internal.YahooChartResponse.Result;
import com.finance.backend.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public abstract class AbstractYahooClient {
    protected final RestTemplate restTemplate;
    protected final String baseUrl;
    private final ZoneId zoneId;
    private final boolean truncateToDays;

    protected AbstractYahooClient(RestTemplate restTemplate, String baseUrl,
                                  ZoneId zoneId, boolean truncateToDays) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.zoneId = zoneId;
        this.truncateToDays = truncateToDays;
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
                        Instant.ofEpochSecond(result.timestamp().get(i)), zoneId);
                if (truncateToDays) {
                    date = date.truncatedTo(ChronoUnit.DAYS);
                }
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

    protected Result fetchChart(String symbol, String range, String interval) {
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
