package com.finance.shared.dto.response;

import java.math.BigDecimal;

/** Crypto-specific {@link MarketAssetMetadata}: market cap, 24h volume, ticker symbol, and USD price. */
public record CryptoMetadata(
        BigDecimal marketCap,
        BigDecimal totalVolume,
        String symbol,
        BigDecimal currentPriceUsd
) implements MarketAssetMetadata {
}
