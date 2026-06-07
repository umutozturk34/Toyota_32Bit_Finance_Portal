package com.finance.market.bond.dto.external;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One historical data point for a bond series as returned by the external rates provider:
 * the coupon rate and clean price observed on a given date.
 *
 * @param rateDate   the observation date
 * @param couponRate the coupon rate effective on that date
 * @param price      the bond's price on that date
 */
public record BondRateItemDto(
        LocalDate rateDate,
        BigDecimal couponRate,
        BigDecimal price
) {}
