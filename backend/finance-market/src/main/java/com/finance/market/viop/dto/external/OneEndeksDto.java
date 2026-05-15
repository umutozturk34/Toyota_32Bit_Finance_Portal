package com.finance.market.viop.dto.external;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OneEndeksDto(
        OffsetDateTime updateDate,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal low,
        BigDecimal high,
        BigDecimal last,
        BigDecimal dayClose,
        BigDecimal quantity,
        BigDecimal volume,
        BigDecimal monthHigh,
        BigDecimal monthLow,
        BigDecimal limitUp,
        BigDecimal limitDown,
        BigDecimal settlement,
        BigDecimal priceStep,
        BigDecimal open,
        BigDecimal weekLow,
        BigDecimal weekHigh,
        BigDecimal weekClose,
        BigDecimal monthClose,
        BigDecimal yearClose,
        BigDecimal preSettlement,
        BigDecimal marketMakerAsk,
        BigDecimal marketMakerBid,
        BigDecimal prevYearClose,
        BigDecimal eqPrice,
        BigDecimal eqQuantity,
        BigDecimal eqRemainingBidQuantity,
        BigDecimal eqRemainingAskQuantity,
        BigDecimal initialMargin,
        String symbol
) { }
