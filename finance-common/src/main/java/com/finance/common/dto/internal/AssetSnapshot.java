package com.finance.common.dto.internal;

import java.math.BigDecimal;

public record AssetSnapshot(
        String code,
        String name,
        String image,
        BigDecimal priceTry,
        BigDecimal changeAmount,
        BigDecimal changePercent,
        String currency
) {
    public AssetSnapshot(String code, String name, String image, BigDecimal priceTry,
                         BigDecimal changeAmount, BigDecimal changePercent) {
        this(code, name, image, priceTry, changeAmount, changePercent, "TRY");
    }
}
