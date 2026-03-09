package com.finance.backend.dto.external;
import java.math.BigDecimal;
public record CoinGeckoMarketDto(
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
