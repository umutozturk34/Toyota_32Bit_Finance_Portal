package com.finance.backend.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

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
