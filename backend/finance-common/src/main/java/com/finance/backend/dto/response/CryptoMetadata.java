package com.finance.backend.dto.response;

import java.math.BigDecimal;

public record CryptoMetadata(
        BigDecimal marketCap,
        BigDecimal totalVolume,
        String symbol,
        BigDecimal currentPriceUsd
) implements MarketAssetMetadata {
}
