package com.finance.backend.dto.response;

import com.finance.backend.model.FundType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FundCandleResponse(
        LocalDateTime candleDate,
        FundType fundType,
        BigDecimal price,
        BigDecimal bulletinPrice,
        BigDecimal shareCount,
        BigDecimal investorCount,
        BigDecimal portfolioSize
) {}
