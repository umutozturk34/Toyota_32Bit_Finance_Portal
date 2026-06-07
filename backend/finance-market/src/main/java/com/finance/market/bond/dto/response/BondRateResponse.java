package com.finance.market.bond.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single point in a bond's rate history: the coupon {@code rate} and quoted {@code price}
 * observed on a given {@code date}. Used to render bond rate/price time series to clients.
 */
public record BondRateResponse(
        LocalDate date,
        BigDecimal rate,
        BigDecimal price
) {}
