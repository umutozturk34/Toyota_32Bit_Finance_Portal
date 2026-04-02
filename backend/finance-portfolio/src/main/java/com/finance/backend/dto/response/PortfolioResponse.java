package com.finance.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PortfolioResponse(
        Long id,
        String name,
        BigDecimal cashBalanceTry,
        LocalDateTime createdAt
) {}
