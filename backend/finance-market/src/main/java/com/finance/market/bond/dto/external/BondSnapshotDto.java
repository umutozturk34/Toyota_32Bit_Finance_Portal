package com.finance.market.bond.dto.external;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A current point-in-time snapshot of a bond series from the external provider, combining its
 * identifiers, latest clean price and coupon rate, maturity window and display name.
 *
 * @param seriesCode    the provider's series code
 * @param isinCode      the series' ISIN identifier
 * @param cleanPrice    the latest clean (ex-accrued-interest) price
 * @param couponRate    the current coupon rate
 * @param maturityStart start of the maturity window
 * @param maturityEnd   end of the maturity window
 * @param serieName     the human-readable series name
 */
public record BondSnapshotDto(
        String seriesCode,
        String isinCode,
        BigDecimal cleanPrice,
        BigDecimal couponRate,
        LocalDate maturityStart,
        LocalDate maturityEnd,
        String serieName
) {}
