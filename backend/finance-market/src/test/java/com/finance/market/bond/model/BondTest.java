package com.finance.market.bond.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BondTest {

    private Bond bond() {
        Bond b = new Bond();
        b.setSeriesCode("S1");
        b.setIsinCode("ISIN1");
        return b;
    }

    @Test
    void isDiscounted_returnsTrue_whenBondTypeDiscounted() {
        Bond b = bond();
        b.setBondType(BondType.DISCOUNTED);

        assertThat(b.isDiscounted()).isTrue();
    }

    @Test
    void isDiscounted_returnsFalse_whenBondTypeNull() {
        Bond b = bond();

        assertThat(b.isDiscounted()).isFalse();
    }

    @Test
    void isFloating_returnsFalse_whenBondTypeNull() {
        Bond b = bond();

        assertThat(b.isFloating()).isFalse();
    }

    @Test
    void resolveNextCouponDate_setsNull_whenMaturityStartMissing() {
        Bond b = bond();
        b.setMaturityStart(null);
        b.setMaturityEnd(LocalDate.now().plusYears(5));

        b.resolveNextCouponDate();

        assertThat(b.getNextCouponDate()).isNull();
    }

    @Test
    void resolveNextCouponDate_setsNull_whenMaturityEndInPast() {
        Bond b = bond();
        b.setMaturityStart(LocalDate.now().minusYears(10));
        b.setMaturityEnd(LocalDate.now().minusYears(1));

        b.resolveNextCouponDate();

        assertThat(b.getNextCouponDate()).isNull();
    }

    @Test
    void resolveNextCouponDate_advancesByHalfYear_pastToday() {
        Bond b = bond();
        b.setMaturityStart(LocalDate.now().minusYears(2));
        b.setMaturityEnd(LocalDate.now().plusYears(5));

        b.resolveNextCouponDate();

        assertThat(b.getNextCouponDate()).isAfter(LocalDate.now());
    }

    @Test
    void resolveNextCouponDate_clampsToMaturityEnd_whenCouponWouldExceed() {
        Bond b = bond();
        LocalDate end = LocalDate.now().plusMonths(2);
        b.setMaturityStart(LocalDate.now().minusYears(2));
        b.setMaturityEnd(end);

        b.resolveNextCouponDate();

        assertThat(b.getNextCouponDate()).isEqualTo(end);
    }

    @Test
    void scaleFields_appliesScaleToMonetaryFields() {
        Bond b = bond();
        b.setCouponRate(new BigDecimal("10.123456"));
        b.setSimpleYield(new BigDecimal("8.987654"));
        b.setBaseIndex(new BigDecimal("100.123456"));

        b.scaleFields(2);

        assertThat(b.getCouponRate().scale()).isEqualTo(2);
        assertThat(b.getSimpleYield().scale()).isEqualTo(2);
        assertThat(b.getBaseIndex().scale()).isEqualTo(2);
    }

    @Test
    void getCode_returnsSeriesCode() {
        Bond b = bond();
        b.setSeriesCode("ABC");

        assertThat(b.getCode()).isEqualTo("ABC");
    }

    @Test
    void getPriceTry_returnsNull_forBondsByDesign() {
        assertThat(bond().getPriceTry()).isNull();
    }
}
