package com.finance.market.core.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CandleResponse(
        LocalDateTime candleDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
) {}
