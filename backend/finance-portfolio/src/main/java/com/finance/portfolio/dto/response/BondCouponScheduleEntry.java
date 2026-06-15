package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One coupon in a bond holding's payment schedule: the payment {@code date}, the per-100-nominal coupon
 * {@code ratePer100} (.ORAN) in effect on that date — historical resets for a floater — the resulting full-position
 * {@code amountTry} ({@code ratePer100 × quantity ÷ 100}), and its {@code status} relative to the holder:
 * {@code BEFORE_ENTRY} (paid before they bought), {@code RECEIVED} (paid while held), or {@code UPCOMING}.
 */
public record BondCouponScheduleEntry(
        LocalDate date,
        BigDecimal ratePer100,
        BigDecimal amountTry,
        String status
) {
}
