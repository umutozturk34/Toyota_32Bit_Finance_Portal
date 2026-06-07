package com.finance.market.crypto.dto.external;

import java.math.BigDecimal;

/**
 * Outbound snapshot of a single coin's market state exposed to clients of this module.
 * Mirrors the fields harvested from CoinGecko's markets endpoint but uses this module's
 * own naming so the external provider's wire format does not leak into the API. Monetary
 * figures ({@code currentPrice}, {@code marketCap}, {@code totalVolume}) are in the
 * provider's quote currency (USD), and {@code priceChange24h}/{@code priceChangePercentage24h}
 * describe the absolute and relative move over the trailing 24 hours.
 *
 * @param id                       CoinGecko coin id (stable primary key, e.g. {@code bitcoin})
 * @param symbol                   ticker symbol (e.g. {@code btc})
 * @param name                     human-readable coin name
 * @param image                    URL of the coin's icon
 * @param currentPrice             latest price in the quote currency
 * @param priceChange24h           absolute price change over the last 24 hours
 * @param priceChangePercentage24h percentage price change over the last 24 hours
 * @param marketCap                total market capitalization
 * @param totalVolume              traded volume over the last 24 hours
 */
public record CoinGeckoSnapshotDto(
        String id,
        String symbol,
        String name,
        String image,
        BigDecimal currentPrice,
        BigDecimal priceChange24h,
        BigDecimal priceChangePercentage24h,
        BigDecimal marketCap,
        BigDecimal totalVolume
) {}
