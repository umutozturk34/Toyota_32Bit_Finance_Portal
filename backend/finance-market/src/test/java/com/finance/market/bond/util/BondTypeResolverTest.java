package com.finance.market.bond.util;

import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.model.BondType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BondTypeResolverTest {

    private static final BigDecimal AUCTION_THRESHOLD = new BigDecimal("30");
    private static final BigDecimal CPI_FIXED_THRESHOLD = new BigDecimal("2");

    @Test
    void resolve_defaultsToFixedCoupon_whenIsinIsNull() {
        Bond bond = bond(null, "S1", null, null);

        BondType result = BondTypeResolver.resolve(bond, List.of(), AUCTION_THRESHOLD, CPI_FIXED_THRESHOLD);

        assertThat(result).isEqualTo(BondType.FIXED_COUPON);
    }

    @Test
    void resolve_returnsDiscounted_whenIsinStartsWithTRB() {
        Bond bond = bond("TRB001", "S1", new BigDecimal("95"), null);

        BondType result = BondTypeResolver.resolve(bond, List.of(), AUCTION_THRESHOLD, CPI_FIXED_THRESHOLD);

        assertThat(result).isEqualTo(BondType.DISCOUNTED);
    }

    @Test
    void resolve_returnsSukukCpi_whenIsinTRDAndBaseIndexHigh() {
        Bond bond = bond("TRD001", "S1", new BigDecimal("150"), new BigDecimal("3"));

        BondType result = BondTypeResolver.resolve(bond, List.of(), AUCTION_THRESHOLD, CPI_FIXED_THRESHOLD);

        assertThat(result).isEqualTo(BondType.SUKUK_CPI);
    }

    @Test
    void resolve_returnsFloatingCpi_whenIsinTRTAndBaseIndexHigh() {
        Bond bond = bond("TRT001", "S1", new BigDecimal("150"), new BigDecimal("3"));

        BondType result = BondTypeResolver.resolve(bond, List.of(), AUCTION_THRESHOLD, CPI_FIXED_THRESHOLD);

        assertThat(result).isEqualTo(BondType.FLOATING_CPI);
    }

    @Test
    void resolve_returnsDiscounted_whenCouponRateMissingOrZero() {
        Bond noRate = bond("TRT001", "S1", new BigDecimal("100"), null);
        Bond zeroRate = bond("TRT002", "S2", new BigDecimal("100"), BigDecimal.ZERO);

        BondType r1 = BondTypeResolver.resolve(noRate, List.of(), AUCTION_THRESHOLD, CPI_FIXED_THRESHOLD);
        BondType r2 = BondTypeResolver.resolve(zeroRate, List.of(), AUCTION_THRESHOLD, CPI_FIXED_THRESHOLD);

        assertThat(r1).isEqualTo(BondType.DISCOUNTED);
        assertThat(r2).isEqualTo(BondType.DISCOUNTED);
    }

    @Test
    void resolve_returnsFloatingCpi_whenCouponBelowCpiFixedThreshold() {
        Bond bond = bond("TRT001", "S1", new BigDecimal("100"), new BigDecimal("1"));

        BondType result = BondTypeResolver.resolve(bond, List.of(), AUCTION_THRESHOLD, CPI_FIXED_THRESHOLD);

        assertThat(result).isEqualTo(BondType.FLOATING_CPI);
    }

    @Test
    void resolve_returnsSukukCpi_whenSukukAndCouponBelowCpiThreshold() {
        Bond bond = bond("TRD001", "S1", new BigDecimal("100"), new BigDecimal("1"));

        BondType result = BondTypeResolver.resolve(bond, List.of(), AUCTION_THRESHOLD, CPI_FIXED_THRESHOLD);

        assertThat(result).isEqualTo(BondType.SUKUK_CPI);
    }

    @Test
    void resolve_returnsFixedCoupon_whenNoRateChangesAndHighRate() {
        Bond bond = bond("TRT001", "S1", new BigDecimal("100"), new BigDecimal("40"));
        List<BondRateHistory> stable = List.of(rateAt("40"), rateAt("40"));

        BondType result = BondTypeResolver.resolve(bond, stable, AUCTION_THRESHOLD, CPI_FIXED_THRESHOLD);

        assertThat(result).isEqualTo(BondType.FIXED_COUPON);
    }

    @Test
    void resolve_returnsFloatingAuction_whenRateChangesAndAtOrAboveAuctionThreshold() {
        Bond bond = bond("TRT001", "S1", new BigDecimal("100"), new BigDecimal("40"));
        List<BondRateHistory> changing = List.of(rateAt("40"), rateAt("38"));

        BondType result = BondTypeResolver.resolve(bond, changing, AUCTION_THRESHOLD, CPI_FIXED_THRESHOLD);

        assertThat(result).isEqualTo(BondType.FLOATING_AUCTION);
    }

    @Test
    void resolve_returnsFloatingTlref_whenRateChangesAndBelowAuctionThreshold() {
        Bond bond = bond("TRT001", "S1", new BigDecimal("100"), new BigDecimal("10"));
        List<BondRateHistory> changing = List.of(rateAt("10"), rateAt("15"));

        BondType result = BondTypeResolver.resolve(bond, changing, AUCTION_THRESHOLD, CPI_FIXED_THRESHOLD);

        assertThat(result).isEqualTo(BondType.FLOATING_TLREF);
    }

    @Test
    void resolve_returnsSukukFixed_whenSukukAndNoRateChanges() {
        Bond bond = bond("TRD001", "S1", new BigDecimal("100"), new BigDecimal("40"));
        List<BondRateHistory> stable = List.of(rateAt("40"), rateAt("40"));

        BondType result = BondTypeResolver.resolve(bond, stable, AUCTION_THRESHOLD, CPI_FIXED_THRESHOLD);

        assertThat(result).isEqualTo(BondType.SUKUK_FIXED);
    }

    @Test
    void resolve_returnsSukukCpi_whenSukukAndRateChanges() {
        Bond bond = bond("TRD001", "S1", new BigDecimal("100"), new BigDecimal("40"));
        List<BondRateHistory> changing = List.of(rateAt("40"), rateAt("35"));

        BondType result = BondTypeResolver.resolve(bond, changing, AUCTION_THRESHOLD, CPI_FIXED_THRESHOLD);

        assertThat(result).isEqualTo(BondType.SUKUK_CPI);
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
