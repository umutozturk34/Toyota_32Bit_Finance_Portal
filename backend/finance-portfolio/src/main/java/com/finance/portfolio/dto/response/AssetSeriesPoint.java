package com.finance.portfolio.dto.response;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AssetSeriesPoint(
        LocalDateTime timestamp,
        BigDecimal unitPriceTry,
        BigDecimal marketValueTry,
        BigDecimal pnlTry,
        BigDecimal dailyPnlTry,
        BigDecimal dailyPnlPercent
) {}
