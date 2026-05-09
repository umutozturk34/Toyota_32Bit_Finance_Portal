package com.finance.market.commodity.model;

import java.math.BigDecimal;

public record CommoditySnapshotInput(
        BigDecimal tryPrice,
        BigDecimal tryPreviousClose,
        BigDecimal usdPrice,
        BigDecimal usdPreviousClose,
        BigDecimal tryOpenPrice,
        BigDecimal tryDayHigh,
        BigDecimal tryDayLow,
        Long volume
) {}
