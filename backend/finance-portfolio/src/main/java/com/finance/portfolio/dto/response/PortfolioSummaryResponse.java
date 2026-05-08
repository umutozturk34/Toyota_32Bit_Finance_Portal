package com.finance.portfolio.dto.response;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.math.BigDecimal;

public record PortfolioSummaryResponse(
        BigDecimal totalValueTry,
        BigDecimal totalEntryValueTry,
        BigDecimal totalPnlTry,
        BigDecimal pnlPercent,
        BigDecimal dailyPnlTry,
        BigDecimal dailyPnlPercent
) {}
