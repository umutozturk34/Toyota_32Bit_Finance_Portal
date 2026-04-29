package com.finance.backend.mapper;

import com.finance.backend.dto.external.YahooStockQuoteDto;
import com.finance.backend.dto.internal.YahooChartResponse.Meta;
import com.finance.backend.dto.internal.YahooChartResponse.Quote;
import com.finance.backend.dto.internal.YahooChartResponse.Result;
import com.finance.backend.util.YahooMetaHelpers;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

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
