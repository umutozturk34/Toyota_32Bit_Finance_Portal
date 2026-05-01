package com.finance.backend.util;

import com.finance.backend.model.Bond;
import com.finance.backend.model.BondType;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Log4j2
public final class BondYieldCalculator {

    private static final int INTERMEDIATE_SCALE = 10;
    private static final int OUTPUT_SCALE = 4;
    private static final BigDecimal SEMI_ANNUAL_FACTOR = new BigDecimal("2");

    private BondYieldCalculator() {
    }

    public static BigDecimal compute(Bond bond, BigDecimal faceValue, int daysInYear) {
        BigDecimal baseIndex = bond.getBaseIndex();
        if (baseIndex == null || baseIndex.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("Cannot calculate yield for {}: baseIndex is null or zero", bond.getIsinCode());
            return null;
        }
        if (bond.getBondType() == BondType.DISCOUNTED) {
            return discountedYield(bond, faceValue, baseIndex, daysInYear);
        }
        if (bond.getBondType() != null && bond.getBondType().isFloating()) {
            return null;
        }
        BigDecimal couponRate = bond.getCouponRate();
        if (couponRate == null || couponRate.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return couponYield(couponRate, baseIndex, faceValue);
    }

    private static BigDecimal discountedYield(Bond bond, BigDecimal faceValue,
                                               BigDecimal baseIndex, int daysInYear) {
        if (bond.getMaturityEnd() == null) return null;
        long days = ChronoUnit.DAYS.between(LocalDate.now(), bond.getMaturityEnd());
        if (days <= 0) return null;
        BigDecimal rawYield = faceValue.subtract(baseIndex)
                .divide(baseIndex, INTERMEDIATE_SCALE, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(daysInYear))
                .divide(new BigDecimal(days), INTERMEDIATE_SCALE, RoundingMode.HALF_UP)
                .multiply(faceValue)
                .setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
        return rawYield.compareTo(BigDecimal.ZERO) < 0 ? null : rawYield;
    }

    private static BigDecimal couponYield(BigDecimal couponRate, BigDecimal baseIndex, BigDecimal faceValue) {
        BigDecimal annualCoupon = couponRate.multiply(SEMI_ANNUAL_FACTOR);
        BigDecimal rawYield = annualCoupon
                .divide(baseIndex, INTERMEDIATE_SCALE, RoundingMode.HALF_UP)
                .multiply(faceValue)
                .setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
        return rawYield.compareTo(BigDecimal.ZERO) < 0 ? null : rawYield;
    }
}
