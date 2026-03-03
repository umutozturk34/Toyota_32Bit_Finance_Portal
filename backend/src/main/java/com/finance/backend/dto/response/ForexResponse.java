package com.finance.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ForexResponse(
        String currencyCode,
        String currencyName,
        BigDecimal currentPrice,
        BigDecimal sellingPrice,
        BigDecimal change24h,
        BigDecimal changePercent24h,
        BigDecimal forexBuying,
        BigDecimal forexSelling,
        BigDecimal banknoteBuying,
        BigDecimal banknoteSelling,
        LocalDateTime updatedAt,
        LocalDateTime tcmbUpdatedAt,
        LocalDateTime yahooUpdatedAt
) {}
