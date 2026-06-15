package com.finance.portfolio.dto.response;

import java.time.LocalDateTime;

/**
 * Basic portfolio identity returned by list/create/rename endpoints. {@code type} is the portfolio's
 * kind enum name ({@code "SPOT"}/{@code "FIXED"}); the frontend uses it to pick the spot vs. fixed-income
 * view instead of the retired within-portfolio toggle.
 */
public record PortfolioResponse(
        Long id,
        String name,
        String type,
        LocalDateTime createdAt
) {}
