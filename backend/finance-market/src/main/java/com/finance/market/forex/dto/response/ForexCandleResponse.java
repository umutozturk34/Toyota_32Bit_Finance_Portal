package com.finance.market.forex.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ForexCandleResponse(
        LocalDateTime candleDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal sellingPrice,
        BigDecimal buyingPrice,
        BigDecimal effectiveBuyingPrice,
        BigDecimal effectiveSellingPrice
) {
}
