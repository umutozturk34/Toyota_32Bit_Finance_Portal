package com.finance.portfolio.dto.response;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PerformancePoint(
        LocalDateTime timestamp,
        BigDecimal totalValueTry,
        BigDecimal totalPnlTry,
        BigDecimal pnlPercent,
        List<PerformanceAssetDetail> details,
        List<PerformanceEvent> events
) {}
