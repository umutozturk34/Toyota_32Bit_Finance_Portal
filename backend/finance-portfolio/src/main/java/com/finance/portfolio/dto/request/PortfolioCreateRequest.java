package com.finance.portfolio.dto.request;

import jakarta.validation.constraints.Size;

/** Request body to create or rename a portfolio. */
public record PortfolioCreateRequest(@Size(max = 25) String name) {}
