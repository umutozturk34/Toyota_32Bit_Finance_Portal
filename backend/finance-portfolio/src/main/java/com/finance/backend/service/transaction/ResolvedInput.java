package com.finance.backend.service.transaction;

import java.math.BigDecimal;

public record ResolvedInput(
        BigDecimal quantity,
        BigDecimal totalCostTry
) {}
