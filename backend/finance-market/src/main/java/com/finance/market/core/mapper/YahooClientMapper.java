package com.finance.market.core.mapper;

import com.finance.common.config.AppProperties;
import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.core.dto.external.YahooQuoteDto;
import com.finance.market.core.dto.internal.YahooChartFullResult;
import com.finance.market.core.dto.internal.YahooChartResponse.Quote;
import com.finance.market.core.dto.internal.YahooChartResponse.Result;
import com.finance.market.core.util.YahooMetaHelpers;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps Yahoo chart results into quote and candle DTOs in the app timezone. Candles fill gaps
 * pragmatically: a null close falls back to the regular-market price, and missing open/high/low
 * default to that close; fully-empty bars are dropped. Optionally truncates timestamps to day.
 */
@Component
public class YahooClientMapper {

    private final ZoneId appZone;

    /**
     * Resolves the application timezone used to convert Yahoo epoch timestamps into local dates.
     *
     * @param appProperties application config supplying the configured timezone id
     */
    public YahooClientMapper(AppProperties appProperties) {
        this.appZone = ZoneId.of(appProperties.getTimezone());
    }

    /**
     * Maps a Yahoo chart result into a combined quote-plus-candles bundle.
     *
     * @param result         raw Yahoo chart result
     * @param truncateToDays whether candle timestamps are floored to day granularity
     * @return the quote DTO paired with the mapped candle series
     */
    public YahooChartFullResult<YahooQuoteDto> toFullResult(Result result, boolean truncateToDays) {
        return new YahooChartFullResult<>(toQuoteDto(result), toCandleDtos(result, truncateToDays));
    }

    /**
     * Extracts the latest-quote DTO from a chart result, resolving the previous close and opening
     * price from metadata with fallbacks to the embedded quote series.
     */
    public YahooQuoteDto toQuoteDto(Result result) {
        var meta = result.meta();
        Quote firstQuote = result.firstQuote();
        return new YahooQuoteDto(
                meta.regularMarketPrice(),
                YahooMetaHelpers.resolvePreviousClose(firstQuote, meta.previousClose()),
                YahooMetaHelpers.latestNonNull(firstQuote == null ? null : firstQuote.open()),
                meta.dayHigh(),
                meta.dayLow(),
                meta.volume(),
                meta.regularMarketChange(),
                meta.regularMarketChangePercent()
        );
    }

    /**
     * Maps the quote series of a chart result into candle DTOs. Returns an empty list when there
     * are no timestamps or quote data. For each bar, fully-empty rows are skipped; a missing close
     * falls back to the regular-market price (and the bar is skipped if that is also absent), and
     * missing open/high/low default to the resolved close. Timestamps are converted to the app
     * timezone and optionally truncated to whole days; volume is matched positionally when present.
     *
     * @param result         raw Yahoo chart result
     * @param truncateToDays whether to floor each candle timestamp to day granularity
     * @return the mapped, gap-filled candle series in source order
     */
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
