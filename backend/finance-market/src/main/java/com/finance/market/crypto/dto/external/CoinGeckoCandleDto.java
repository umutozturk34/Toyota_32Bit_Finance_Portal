package com.finance.market.crypto.dto.external;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;
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
