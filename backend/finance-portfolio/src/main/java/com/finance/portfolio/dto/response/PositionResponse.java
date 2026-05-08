package com.finance.portfolio.dto.response;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PositionResponse(
        Long id,
        String assetType,
        String assetCode,
        String assetName,
        String assetImage,
        BigDecimal quantity,
        LocalDateTime entryDate,
        BigDecimal entryPrice,
        BigDecimal currentPriceTry,
        BigDecimal entryValueTry,
        BigDecimal marketValueTry,
        BigDecimal pnlTry,
        BigDecimal pnlPercent
) {}
