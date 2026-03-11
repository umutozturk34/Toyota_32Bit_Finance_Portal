package com.finance.backend.mapper;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.dto.internal.YahooChartResponse.Quote;
import com.finance.backend.dto.internal.YahooChartResponse.Result;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class YahooClientMapper {

    private final ZoneId appZone;

    public YahooClientMapper(AppProperties appProperties) {
        this.appZone = ZoneId.of(appProperties.getTimezone());
    }

    public YahooQuoteDto toQuoteDto(Result result) {
        return new YahooQuoteDto(result.meta().regularMarketPrice(), result.meta().previousClose());
    }

    public List<YahooCandleDto> toCandleDtos(Result result, boolean truncateToDays) {
        Quote quote = result.firstQuote();
        if (result.timestamp() == null || result.timestamp().isEmpty() || quote == null) {
            return List.of();
        }
        List<YahooCandleDto> candles = new ArrayList<>(result.timestamp().size());
        for (int i = 0; i < result.timestamp().size(); i++) {
            if (!quote.isValidAt(i)) continue;
            LocalDateTime date = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(result.timestamp().get(i)), appZone);
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
    }
}
