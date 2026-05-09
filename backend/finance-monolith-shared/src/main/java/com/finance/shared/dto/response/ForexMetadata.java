package com.finance.shared.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ForexMetadata(
        BigDecimal sellingPrice,
        BigDecimal forexBuying,
        BigDecimal forexSelling,
        Integer unit,
        BigDecimal banknoteBuying,
        BigDecimal banknoteSelling,
        LocalDateTime yahooUpdatedAt,
        LocalDateTime tcmbUpdatedAt,
        BigDecimal openPrice,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        Long volume
) implements MarketAssetMetadata {
}
