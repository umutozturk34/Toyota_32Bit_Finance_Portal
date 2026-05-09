package com.finance.market.core.dto.external;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record YahooCandleDto(
        LocalDateTime candleDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
) {}
