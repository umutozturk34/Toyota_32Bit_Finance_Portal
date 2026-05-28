package com.finance.market.stock.mapper;

import com.finance.market.stock.dto.external.YahooStockQuoteDto;
import com.finance.market.core.dto.internal.YahooChartResponse.Meta;
import com.finance.market.core.dto.internal.YahooChartResponse.Quote;
import com.finance.market.core.dto.internal.YahooChartResponse.Result;
import com.finance.market.core.util.YahooMetaHelpers;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Builds a stock quote DTO from a Yahoo chart result, defaulting name to the symbol, exchange to
 * BIST and currency to TRY, and deriving open/previous-close from the series when meta lacks them.
 */
@Component
public class YahooStockQuoteMapper {

    public YahooStockQuoteDto toDto(Result result, String symbol) {
        Meta meta = result.meta();
        Quote quote = result.firstQuote();
        BigDecimal openPrice = YahooMetaHelpers.latestNonNull(quote == null ? null : quote.open());
        BigDecimal previousClose = YahooMetaHelpers.resolvePreviousClose(quote, meta.previousClose());

        return new YahooStockQuoteDto(
                symbol,
                Objects.toString(meta.longName(), Objects.toString(meta.shortName(), symbol)),
                meta.regularMarketPrice(),
                previousClose,
                meta.regularMarketChange(),
                meta.regularMarketChangePercent(),
                openPrice,
                meta.dayHigh(),
                meta.dayLow(),
                meta.volume() != null ? meta.volume() : 0L,
                Objects.toString(meta.exchangeName(), "BIST"),
                Objects.toString(meta.currency(), "TRY")
        );
    }
}
