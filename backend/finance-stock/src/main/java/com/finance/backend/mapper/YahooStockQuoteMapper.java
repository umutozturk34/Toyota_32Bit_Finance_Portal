package com.finance.backend.mapper;

import com.finance.backend.dto.external.YahooStockQuoteDto;
import com.finance.backend.dto.internal.YahooChartResponse.Meta;
import com.finance.backend.dto.internal.YahooChartResponse.Quote;
import com.finance.backend.dto.internal.YahooChartResponse.Result;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Component
public class YahooStockQuoteMapper {

    public YahooStockQuoteDto toDto(Result result, String symbol) {
        Meta meta = result.meta();
        Quote quote = result.firstQuote();
        BigDecimal openPrice = (quote != null && quote.open() != null && !quote.open().isEmpty())
                ? quote.open().getFirst() : null;
        BigDecimal previousClose = resolvePreviousClose(quote, meta.previousClose());

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

    private static BigDecimal resolvePreviousClose(Quote quote, BigDecimal metaPreviousClose) {
        if (metaPreviousClose != null) return metaPreviousClose;
        if (quote == null || quote.close() == null) return null;
        List<BigDecimal> closes = quote.close();
        for (int i = closes.size() - 2; i >= 0; i--) {
            if (closes.get(i) != null) return closes.get(i);
        }
        return null;
    }
}
