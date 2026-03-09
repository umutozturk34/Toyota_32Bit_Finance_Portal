package com.finance.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FundCandleResponse(
        LocalDateTime candleDate,
        String fundType,
        BigDecimal price,
        BigDecimal bulletinPrice,
        BigDecimal shareCount,
        BigDecimal investorCount,
        BigDecimal portfolioSize
) {}
