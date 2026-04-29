package com.finance.backend.mapper;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.dto.internal.YahooChartFullResult;
import com.finance.backend.dto.internal.YahooChartResponse.Quote;
import com.finance.backend.dto.internal.YahooChartResponse.Result;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

    public YahooChartFullResult<YahooQuoteDto> toFullResult(Result result, boolean truncateToDays) {
        return new YahooChartFullResult<>(toQuoteDto(result), toCandleDtos(result, truncateToDays));
    }

    public YahooQuoteDto toQuoteDto(Result result) {
        var meta = result.meta();
        Quote firstQuote = result.firstQuote();
        BigDecimal openPrice = (firstQuote != null && firstQuote.open() != null && !firstQuote.open().isEmpty())
                ? firstQuote.open().getFirst() : null;
        return new YahooQuoteDto(
                meta.regularMarketPrice(),
                resolvePreviousClose(firstQuote, meta.previousClose()),
                openPrice,
                meta.dayHigh(),
                meta.dayLow(),
                meta.volume()
        );
    }

    private static BigDecimal resolvePreviousClose(Quote quote, BigDecimal metaPreviousClose) {
        if (metaPreviousClose != null) return metaPreviousClose;
        if (quote == null || quote.close() == null) return null;
        List<BigDecimal> closes = quote.close();
        for (int i = closes.size() - 2; i >= 0; i--) {
            if (closes.get(i) != null) return closes.get(i);
        }
        return null;
    }

    public List<YahooCandleDto> toCandleDtos(Result result, boolean truncateToDays) {
        Quote quote = result.firstQuote();
        if (result.timestamp() == null || result.timestamp().isEmpty() || quote == null) {
            return List.of();
        }
        BigDecimal marketPrice = result.meta() != null ? result.meta().regularMarketPrice() : null;
        List<YahooCandleDto> candles = new ArrayList<>(result.timestamp().size());
        for (int i = 0; i < result.timestamp().size(); i++) {
            BigDecimal open = safeGet(quote.open(), i);
            BigDecimal high = safeGet(quote.high(), i);
            BigDecimal low = safeGet(quote.low(), i);
            BigDecimal close = safeGet(quote.close(), i);
            if (open == null && high == null && low == null && close == null) continue;
            if (close == null) close = marketPrice;
            if (close == null) continue;
            if (open == null) open = close;
            if (high == null) high = close;
            if (low == null) low = close;
            LocalDateTime date = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(result.timestamp().get(i)), appZone);
            if (truncateToDays) {
                date = date.truncatedTo(ChronoUnit.DAYS);
            }
            Long vol = (quote.volume() != null && i < quote.volume().size())
                    ? quote.volume().get(i) : null;
            candles.add(new YahooCandleDto(date, open, high, low, close, vol));
        }
        return candles;
    }

    private static <T> T safeGet(List<T> list, int index) {
        return (list != null && index < list.size()) ? list.get(index) : null;
    }
}
