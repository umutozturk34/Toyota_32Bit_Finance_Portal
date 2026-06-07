package com.finance.market.crypto.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Inbound deserialization target for a single entry of CoinGecko's {@code /coins/markets}
 * response. The {@link JsonProperty} annotations bridge CoinGecko's snake_case wire field
 * names to this record's camelCase components; this type stays internal to the crypto fetch
 * pipeline and is mapped onto domain/outbound types before leaving the module. Monetary values
 * are in the requested quote currency (USD).
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
public record CoinGeckoMarketDto(
        String id,
        String symbol,
        String name,
        String image,
        @JsonProperty("current_price") BigDecimal currentPrice,
        @JsonProperty("price_change_24h") BigDecimal priceChange24h,
        @JsonProperty("price_change_percentage_24h") BigDecimal priceChangePercentage24h,
        @JsonProperty("market_cap") BigDecimal marketCap,
        @JsonProperty("total_volume") BigDecimal totalVolume
) {}
