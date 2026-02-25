package com.finance.backend.dto.external;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record CoinGeckoCandleDto(
        String coinId,
        LocalDateTime candleDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
) {}
