package com.finance.market.stock.dto.external;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;
import java.math.BigDecimal;
public record YahooStockQuoteDto(
        String symbol,
        String name,
        BigDecimal currentPrice,
        BigDecimal previousClose,
        BigDecimal changeAmount,
        BigDecimal changePercent,
        BigDecimal openPrice,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        long volume,
        String exchange,
        String currency
) {}
