package com.finance.market.commodity.model;

import java.math.BigDecimal;

/** Inputs for a commodity snapshot: TRY-converted OHLC/price plus the source USD prices and Yahoo change. */
public record CommoditySnapshotInput(
        BigDecimal tryPrice,
        BigDecimal tryPreviousClose,
        BigDecimal usdPrice,
        BigDecimal usdPreviousClose,
        BigDecimal tryOpenPrice,
        BigDecimal tryDayHigh,
        BigDecimal tryDayLow,
        Long volume,
        BigDecimal yahooChangePercent
) {}
