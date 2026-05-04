package com.finance.common.dto.external;
import java.math.BigDecimal;
public record YahooQuoteDto(
        BigDecimal regularMarketPrice,
        BigDecimal previousClose,
        BigDecimal openPrice,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        Long volume
) {}
