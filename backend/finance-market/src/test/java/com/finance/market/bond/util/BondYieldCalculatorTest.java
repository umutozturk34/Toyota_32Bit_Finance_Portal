package com.finance.market.bond.util;

import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BondYieldCalculatorTest {

    private static final BigDecimal FACE_VALUE = new BigDecimal("100");

    @Test
    void compute_returnsNull_whenBaseIndexIsNull() {
        Bond bond = bondBuilder().build();
        bond.setBaseIndex(null);

        BigDecimal yield = BondYieldCalculator.compute(bond, FACE_VALUE, 365);

        assertThat(yield).isNull();
    }

    @Test
    void compute_returnsNull_whenBaseIndexIsZero() {
        Bond bond = bondBuilder().build();
        bond.setBaseIndex(BigDecimal.ZERO);

        BigDecimal yield = BondYieldCalculator.compute(bond, FACE_VALUE, 365);

        assertThat(yield).isNull();
    }

    @Test
    void compute_returnsDiscountedYield_whenBondIsDiscountedAndMaturityFuture() {
        Bond bond = bondBuilder().build();
        bond.setBondType(BondType.DISCOUNTED);
        bond.setBaseIndex(new BigDecimal("90"));
        bond.setMaturityEnd(LocalDate.now().plusDays(365));

        BigDecimal yield = BondYieldCalculator.compute(bond, FACE_VALUE, 365);

        assertThat(yield).isPositive();
    }

    @Test
    void compute_returnsNull_whenDiscountedMaturityNotSet() {
        Bond bond = bondBuilder().build();
        bond.setBondType(BondType.DISCOUNTED);
        bond.setBaseIndex(new BigDecimal("95"));
        bond.setMaturityEnd(null);

        BigDecimal yield = BondYieldCalculator.compute(bond, FACE_VALUE, 365);

        assertThat(yield).isNull();
    }

    @Test
    void compute_returnsNull_whenDiscountedMaturityAlreadyPassed() {
        Bond bond = bondBuilder().build();
        bond.setBondType(BondType.DISCOUNTED);
        bond.setBaseIndex(new BigDecimal("95"));
        bond.setMaturityEnd(LocalDate.now().minusDays(1));

        BigDecimal yield = BondYieldCalculator.compute(bond, FACE_VALUE, 365);

        assertThat(yield).isNull();
    }

    @Test
    void compute_returnsNull_whenDiscountedYieldWouldBeNegative() {
        Bond bond = bondBuilder().build();
        bond.setBondType(BondType.DISCOUNTED);
        bond.setBaseIndex(new BigDecimal("120"));
        bond.setMaturityEnd(LocalDate.now().plusDays(365));

        BigDecimal yield = BondYieldCalculator.compute(bond, FACE_VALUE, 365);

        assertThat(yield).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = BondType.class, names = {"FLOATING_TLREF", "FLOATING_CPI", "FLOATING_AUCTION", "SUKUK_CPI"})
    void compute_returnsNull_whenBondIsFloating(BondType type) {
        Bond bond = bondBuilder().build();
        bond.setBondType(type);
        bond.setBaseIndex(new BigDecimal("100"));
        bond.setCouponRate(new BigDecimal("8"));

        BigDecimal yield = BondYieldCalculator.compute(bond, FACE_VALUE, 365);

        assertThat(yield).isNull();
    }

    @Test
    void compute_returnsCouponYield_whenFixedCouponBondWithPositiveRate() {
        Bond bond = bondBuilder().build();
        bond.setBondType(BondType.FIXED_COUPON);
        bond.setBaseIndex(new BigDecimal("100"));
        bond.setCouponRate(new BigDecimal("8"));

        BigDecimal yield = BondYieldCalculator.compute(bond, FACE_VALUE, 365);

        assertThat(yield).isEqualByComparingTo(new BigDecimal("16.0000"));
    }

    @Test
    void compute_returnsNull_whenCouponRateIsNullOrNonPositive() {
        Bond bond = bondBuilder().build();
        bond.setBondType(BondType.FIXED_COUPON);
        bond.setBaseIndex(new BigDecimal("100"));
        bond.setCouponRate(null);
        assertThat(BondYieldCalculator.compute(bond, FACE_VALUE, 365)).isNull();

        bond.setCouponRate(BigDecimal.ZERO);
        assertThat(BondYieldCalculator.compute(bond, FACE_VALUE, 365)).isNull();

        bond.setCouponRate(new BigDecimal("-1"));
        assertThat(BondYieldCalculator.compute(bond, FACE_VALUE, 365)).isNull();
    }

    @Test
    void compute_returnsCouponYield_whenBondTypeIsNullButCouponSet() {
        Bond bond = bondBuilder().build();
        bond.setBondType(null);
        bond.setBaseIndex(new BigDecimal("100"));
        bond.setCouponRate(new BigDecimal("5"));

        BigDecimal yield = BondYieldCalculator.compute(bond, FACE_VALUE, 365);

        assertThat(yield).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    private Bond.BondBuilder<?, ?> bondBuilder() {
        return Bond.builder().seriesCode("S1").isinCode("TRT123");
    }
}
