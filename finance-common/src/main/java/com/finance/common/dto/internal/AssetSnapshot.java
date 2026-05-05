package com.finance.common.dto.internal;

import java.math.BigDecimal;

public record AssetSnapshot(
        String code,
        String name,
        String image,
        BigDecimal priceTry
) {
}
