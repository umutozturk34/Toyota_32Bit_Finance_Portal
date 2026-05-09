package com.finance.market.fund.dto.response;

import com.finance.market.fund.model.FundType;

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
