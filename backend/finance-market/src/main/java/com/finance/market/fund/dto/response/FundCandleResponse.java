package com.finance.market.fund.dto.response;

import com.finance.market.fund.model.FundType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * API response for one daily fund data point: the observation date and fund type, unit
 * price and exchange-bulletin price, outstanding share count, investor count, and total
 * portfolio size. Mirrors a {@code FundCandle} entity projected for client consumption.
 */
public record FundCandleResponse(
        LocalDateTime candleDate,
        FundType fundType,
        BigDecimal price,
        BigDecimal bulletinPrice,
        BigDecimal shareCount,
        BigDecimal investorCount,
        BigDecimal portfolioSize
) {}
