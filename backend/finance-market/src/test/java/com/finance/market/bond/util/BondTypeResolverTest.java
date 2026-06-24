package com.finance.market.bond.util;

import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.model.BondType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BondTypeResolverTest {

    private static final BigDecimal AUCTION_THRESHOLD = new BigDecimal("30");
    private static final BigDecimal CPI_FIXED_THRESHOLD = new BigDecimal("2");
    private static final BigDecimal GOLD_VALUE_THRESHOLD = new BigDecimal("2000");

    /** Runs the resolver with the test thresholds, so a single arrange covers all three tuning constants. */
    private BondType resolve(Bond bond, List<BondRateHistory> history) {
        return BondTypeResolver.resolve(bond, history, AUCTION_THRESHOLD, CPI_FIXED_THRESHOLD, GOLD_VALUE_THRESHOLD);
    }

    @Test
    void resolve_defaultsToFixedCoupon_whenIsinIsNull() {
        assertThat(resolve(bond(null, "S1", null, null), List.of())).isEqualTo(BondType.FIXED_COUPON);
    }

    @Test
    void resolve_returnsDiscounted_whenIsinStartsWithTRB() {
        assertThat(resolve(bond("TRB001", "S1", new BigDecimal("95"), null), List.of()))
                .isEqualTo(BondType.DISCOUNTED);
    }

    @Test
    void resolve_returnsSukukCpi_whenSukukZeroCouponAndPriceInflated() {
        assertThat(resolve(bond("TRD001", "S1", new BigDecimal("150"), null), List.of()))
                .isEqualTo(BondType.SUKUK_CPI);
    }

    @Test
    void resolve_returnsFloatingCpi_whenZeroCouponAndPriceInflated() {
        assertThat(resolve(bond("TRT001", "S1", new BigDecimal("150"), null), List.of()))
                .isEqualTo(BondType.FLOATING_CPI);
    }

    @Test
    void resolve_returnsFixedCoupon_whenNormalCouponAndPriceAbovePar() {
        // Regression: a nominal bond whose coupon exceeds the market yield trades above par; the price must NOT
        // reclassify it as CPI-linked.
        Bond bond = bond("TRT001", "S1", new BigDecimal("118"), new BigDecimal("18"));
        assertThat(resolve(bond, List.of(rateAt("18"), rateAt("18")))).isEqualTo(BondType.FIXED_COUPON);
    }

    @Test
    void resolve_returnsSukukFixed_whenSukukNormalCouponAndPriceAbovePar() {
        Bond bond = bond("TRD001", "S1", new BigDecimal("120"), new BigDecimal("18"));
        assertThat(resolve(bond, List.of(rateAt("18"), rateAt("18")))).isEqualTo(BondType.SUKUK_FIXED);
    }

    @Test
    void resolve_ignoresBadCoupon_whenCouponFieldHoldsStrayIndexValue() {
        // A coupon far above any real rate (e.g. ~257 / thousands) is bad .ORAN data, NOT a CPI signal — it is
        // ignored and the bond is classified by its (here stable) rate behaviour as a plain coupon bond.
        Bond bond = bond("TRT001", "S1", new BigDecimal("357"), new BigDecimal("257"));
        assertThat(resolve(bond, List.of())).isEqualTo(BondType.FIXED_COUPON);
    }

    @Test
    void resolve_returnsDiscounted_whenCouponRateMissingOrZero() {
        assertThat(resolve(bond("TRT001", "S1", new BigDecimal("100"), null), List.of()))
                .isEqualTo(BondType.DISCOUNTED);
        assertThat(resolve(bond("TRT002", "S2", new BigDecimal("100"), BigDecimal.ZERO), List.of()))
                .isEqualTo(BondType.DISCOUNTED);
    }

    @Test
    void resolve_returnsFloatingCpi_whenCouponBelowCpiFixedThreshold() {
        assertThat(resolve(bond("TRT001", "S1", new BigDecimal("100"), new BigDecimal("1")), List.of()))
                .isEqualTo(BondType.FLOATING_CPI);
    }

    @Test
    void resolve_returnsSukukCpi_whenSukukAndCouponBelowCpiThreshold() {
        assertThat(resolve(bond("TRD001", "S1", new BigDecimal("100"), new BigDecimal("1")), List.of()))
                .isEqualTo(BondType.SUKUK_CPI);
    }

    @Test
    void resolve_returnsFixedCoupon_whenNoRateChangesAndHighRate() {
        Bond bond = bond("TRT001", "S1", new BigDecimal("100"), new BigDecimal("40"));
        assertThat(resolve(bond, List.of(rateAt("40"), rateAt("40")))).isEqualTo(BondType.FIXED_COUPON);
    }

    @Test
    void resolve_returnsFloatingAuction_whenRateChangesAndAtOrAboveAuctionThreshold() {
        Bond bond = bond("TRT001", "S1", new BigDecimal("100"), new BigDecimal("40"));
        assertThat(resolve(bond, List.of(rateAt("40"), rateAt("38")))).isEqualTo(BondType.FLOATING_AUCTION);
    }

    @Test
    void resolve_returnsFloatingTlref_whenRateChangesAndBelowAuctionThreshold() {
        Bond bond = bond("TRT001", "S1", new BigDecimal("100"), new BigDecimal("10"));
        assertThat(resolve(bond, List.of(rateAt("10"), rateAt("15")))).isEqualTo(BondType.FLOATING_TLREF);
    }

    @Test
    void resolve_returnsSukukFixed_whenSukukAndNoRateChanges() {
        Bond bond = bond("TRD001", "S1", new BigDecimal("100"), new BigDecimal("40"));
        assertThat(resolve(bond, List.of(rateAt("40"), rateAt("40")))).isEqualTo(BondType.SUKUK_FIXED);
    }

    @Test
    void resolve_returnsSukukFloating_whenSukukNormalCouponResets() {
        // A sukuk whose NORMAL rental rate resets is a değişken (floating) kira sertifikası — NOT CPI. This is the
        // core fix: previously any rate-changing sukuk was mislabelled SUKUK_CPI.
        Bond bond = bond("TRD001", "S1", new BigDecimal("108"), new BigDecimal("19.92"));
        assertThat(resolve(bond, List.of(rateAt("19.92"), rateAt("17.20")))).isEqualTo(BondType.SUKUK_FLOATING);
    }

    @Test
    void resolve_returnsGold_whenLowCouponAndValueOnGoldScale() {
        // Gold-linked: a tiny rental coupon on a per-unit value in the thousands (gram gold) → GOLD (TRT) /
        // SUKUK_GOLD (TRD), checked before the CPI low-coupon rule.
        assertThat(resolve(bond("TRT270127T15", "S1", new BigDecimal("6287.54"), new BigDecimal("0.40")), List.of()))
                .isEqualTo(BondType.GOLD);
        assertThat(resolve(bond("TRD030726T16", "S1", new BigDecimal("6316.54"), new BigDecimal("0.85")), List.of()))
                .isEqualTo(BondType.SUKUK_GOLD);
    }

    /**
     * Real TCMB-derived cases (static fixture, no live link): each row is an actual bond's ISIN-prefix, value and
     * coupon with its authoritative type, guarding the classifier against the misclassifications found in the DB.
     */
    @ParameterizedTest
    @CsvSource({
            "TRT080131T12, 116.82, 2.65, true,  FLOATING_CPI",    // genuine CPI (low real coupon, per-100)
            "TRT270127T15, 6287.54, 0.40, false, GOLD",            // gold DİBS (per-unit thousands)
            "TRD030726T16, 6316.54, 0.85, false, SUKUK_GOLD",      // gold sukuk
            "TRD061027T41, 108.15, 19.92, true,  SUKUK_FLOATING",  // variable sukuk (was wrongly SUKUK_CPI)
            "TRD160228T14, 100.00, 4.64, true,  SUKUK_FLOATING",   // low-ish but >4 → variable sukuk, not CPI
            "TRT210427T18, 357.08, 257.08, false, FIXED_COUPON",   // stray .ORAN → ignored, not CPI
    })
    void resolve_matchesTcmbAuthoritativeType(String isin, String value, String coupon,
                                              boolean rateChanges, BondType expected) {
        // cpiFixedThreshold 4 mirrors production here so the 2.65-vs-4.64 boundary is exercised faithfully.
        List<BondRateHistory> history = rateChanges
                ? List.of(rateAt(coupon), rateAt(new BigDecimal(coupon).multiply(new BigDecimal("0.9")).toPlainString()))
                : List.of(rateAt(coupon), rateAt(coupon));
        BondType result = BondTypeResolver.resolve(bond(isin, "S", new BigDecimal(value), new BigDecimal(coupon)),
                history, AUCTION_THRESHOLD, new BigDecimal("4"), GOLD_VALUE_THRESHOLD);
        assertThat(result).isEqualTo(expected);
    }

    private Bond bond(String isin, String series, BigDecimal baseIndex, BigDecimal coupon) {
        Bond b = Bond.builder().seriesCode(series).isinCode(isin).build();
        b.setBaseIndex(baseIndex);
        b.setCouponRate(coupon);
        return b;
    }

    private BondRateHistory rateAt(String rate) {
        BondRateHistory r = new BondRateHistory();
        r.setCouponRate(new BigDecimal(rate));
        return r;
    }
}
