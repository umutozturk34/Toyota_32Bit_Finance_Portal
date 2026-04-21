package com.finance.backend.model;

import java.math.BigDecimal;

public record CommoditySnapshotInput(
        BigDecimal tryPrice,
        BigDecimal tryPreviousClose,
        BigDecimal usdPrice,
        BigDecimal usdPreviousClose
) {}
