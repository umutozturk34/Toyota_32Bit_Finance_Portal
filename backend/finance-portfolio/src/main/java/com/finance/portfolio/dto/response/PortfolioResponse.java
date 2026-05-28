package com.finance.portfolio.dto.response;

import java.time.LocalDateTime;

/** Basic portfolio identity returned by list/create/rename endpoints. */
public record PortfolioResponse(
        Long id,
        String name,
        LocalDateTime createdAt
) {}
