package com.finance.common.dto.internal;

import java.math.BigDecimal;

/**
 * Immutable point-in-time view of an asset's latest price and metadata as read from the snapshot
 * cache. {@code priceTry} holds the cached price and {@code currency} its quote currency, which
 * defaults to {@code TRY} via the convenience constructor.
 */
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
