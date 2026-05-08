package com.finance.market.crypto.dto.external;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.math.BigDecimal;

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
