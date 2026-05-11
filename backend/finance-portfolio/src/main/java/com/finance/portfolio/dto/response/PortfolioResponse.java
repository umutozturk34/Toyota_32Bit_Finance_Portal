package com.finance.portfolio.dto.response;

import java.time.LocalDateTime;

public record PortfolioResponse(
        Long id,
        String name,
        LocalDateTime createdAt
) {}
