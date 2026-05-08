package com.finance.portfolio.dto.response;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LotLimitsResponse(
        LocalDate minEntryDate,
        LocalDate maxEntryDate,
        BigDecimal minPriceTry,
        BigDecimal maxPriceTry,
        BigDecimal minQuantity,
        BigDecimal maxQuantity
) {}
