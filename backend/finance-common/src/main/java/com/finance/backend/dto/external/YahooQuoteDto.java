package com.finance.backend.dto.external;
import java.math.BigDecimal;
public record YahooQuoteDto(
        BigDecimal regularMarketPrice,
        BigDecimal previousClose
) {}
