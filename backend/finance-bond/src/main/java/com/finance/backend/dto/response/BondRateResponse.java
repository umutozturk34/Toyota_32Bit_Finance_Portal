package com.finance.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BondRateResponse(
        LocalDate date,
        BigDecimal rate
) {}
