package com.finance.portfolio.fixedincome.bond;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BondCouponService}: straight-line accrued-coupon math with fixed dates (deterministic,
 * never reading {@code now()}). AAA throughout; figures chosen to land on round numbers where possible.
 */
class BondCouponServiceTest {

    private final BondCouponService service = new BondCouponService();

    @Test
    void shouldAccrueStraightLineWithinAnnualPeriod() {
        // Arrange: 36.5% annual coupon, paid ANNUALLY, issued 2026-01-01; 10 days in → 36.5 × 10/365 = 1.00 per 100.
        BondCouponService.CouponAccrual acc = service.accrued(new BigDecimal("36.5"), CouponFrequency.ANNUAL,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 11));

        // Assert
        assertThat(acc.accruedPer100()).isEqualByComparingTo("1.00000000");
        assertThat(acc.dailyAccrualPer100()).isEqualByComparingTo("0.10000000");
        assertThat(acc.lastCouponDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(acc.nextCouponDate()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void shouldStepToCurrentSemiAnnualPeriod() {
        // Arrange: 20% annual, SEMI_ANNUAL (10 per period), issued 2026-01-01; as-of in the 2nd period.
        BondCouponService.CouponAccrual acc = service.accrued(new BigDecimal("20"), CouponFrequency.SEMI_ANNUAL,
                LocalDate.of(2026, 1, 1), LocalDate.of(2031, 1, 1), LocalDate.of(2026, 9, 1));

        // Assert: the surrounding coupon dates are the 2nd period (Jul→Jan), and accrual is positive.
        assertThat(acc.lastCouponDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(acc.nextCouponDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(acc.accruedPer100()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void shouldReturnNoneForZeroCouponBill() {
        // Arrange: a zero-coupon bill never accrues a coupon.
        BondCouponService.CouponAccrual acc = service.accrued(new BigDecimal("15"), CouponFrequency.ZERO_COUPON,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), LocalDate.of(2026, 6, 1));

        // Assert
        assertThat(acc.accruedPer100()).isEqualByComparingTo("0");
        assertThat(acc.lastCouponDate()).isNull();
    }

    @Test
    void shouldReturnNoneBeforeIssueDate() {
        // Arrange: an as-of date before the bond is issued has no accrued coupon.
        BondCouponService.CouponAccrual acc = service.accrued(new BigDecimal("25"), CouponFrequency.SEMI_ANNUAL,
                LocalDate.of(2026, 6, 1), LocalDate.of(2031, 6, 1), LocalDate.of(2026, 1, 1));

        // Assert
        assertThat(acc.accruedPer100()).isEqualByComparingTo("0");
    }

    @Test
    void shouldFreezeAtMaturity_givingNoAccruedCouponAfterRedemption() {
        // Arrange: as-of well past maturity clamps to the maturity date, where the final coupon has just paid.
        BondCouponService.CouponAccrual acc = service.accrued(new BigDecimal("36.5"), CouponFrequency.ANNUAL,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 1));

        // Assert
        assertThat(acc.accruedPer100()).isEqualByComparingTo("0");
    }

    @Test
    void shouldCountFullCouponsPaidSinceEntry() {
        // Arrange: 20% annual, SEMI_ANNUAL (10 per period), issued + entered 2026-01-01; one year on, two
        // coupon dates (Jul, Jan) have paid → 2 × 10 = 20 per 100.
        BondCouponService.CouponsPaid paid = service.couponsPaid(new BigDecimal("20"), CouponFrequency.SEMI_ANNUAL,
                LocalDate.of(2026, 1, 1), LocalDate.of(2031, 1, 1), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));

        // Assert
        assertThat(paid.count()).isEqualTo(2);
        assertThat(paid.totalPer100()).isEqualByComparingTo("20.00000000");
    }

    @Test
    void shouldReturnNoCouponsPaid_whenSoldBeforeFirstCoupon() {
        // Arrange: entered at issue, sold 3 months later — before the first (6-month) coupon date.
        BondCouponService.CouponsPaid paid = service.couponsPaid(new BigDecimal("20"), CouponFrequency.SEMI_ANNUAL,
                LocalDate.of(2026, 1, 1), LocalDate.of(2031, 1, 1), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1));

        // Assert
        assertThat(paid.count()).isZero();
        assertThat(paid.totalPer100()).isEqualByComparingTo("0");
    }

    @Test
    void shouldBuildSchedule_pricingEachCouponAtItsHistoricalRate() {
        // Arrange: a SEMI_ANNUAL floater, issued+entered 2026-01-01, maturing 2028-01-01 (coupons Jul-01/Jan-01).
        // A floater coupon pays the rate fixed at its PERIOD START (the previous coupon date), not the rate current
        // on the payment day. The reset steps 10.00 → 14.00 at the 2026-07-01 coupon, so the first coupon (period
        // start 2026-01-01) is priced at 10 and every later one (period start ≥ 2026-07-01) at 14. As-of 2026-09-01
        // → only the first coupon has been received.
        NavigableMap<LocalDate, BigDecimal> rates = new TreeMap<>();
        rates.put(LocalDate.of(2026, 1, 1), new BigDecimal("10.00"));
        rates.put(LocalDate.of(2026, 7, 1), new BigDecimal("14.00"));

        List<BondCouponService.ScheduleEntry> schedule = service.schedule(rates, new BigDecimal("9.00"),
                CouponFrequency.SEMI_ANNUAL, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1));

        // Assert: 4 coupons; the first RECEIVED at the pre-reset rate, the rest UPCOMING at the reset rate.
        assertThat(schedule).hasSize(4);
        assertThat(schedule.get(0).date()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(schedule.get(0).ratePer100()).isEqualByComparingTo("10.00");
        assertThat(schedule.get(0).status()).isEqualTo("RECEIVED");
        assertThat(schedule.get(1).date()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(schedule.get(1).ratePer100()).isEqualByComparingTo("14.00");
        assertThat(schedule.get(1).status()).isEqualTo("UPCOMING");
        assertThat(schedule.get(3).status()).isEqualTo("UPCOMING");
    }

    @Test
    void shouldPayFinalCouponAtMaturity_whenStepLandsPastMaturity() {
        // Arrange: SEMI_ANNUAL, issued 2026-01-28, maturing 2027-01-27 — the second coupon would step to 2027-01-28,
        // a day PAST maturity, so the final coupon must instead be paid AT maturity (2027-01-27) with the principal.
        List<BondCouponService.ScheduleEntry> schedule = service.schedule(new TreeMap<>(), new BigDecimal("0.40"),
                CouponFrequency.SEMI_ANNUAL, LocalDate.of(2026, 1, 28), LocalDate.of(2027, 1, 27),
                LocalDate.of(2026, 1, 28), LocalDate.of(2026, 6, 1));

        // Assert: two coupons — the regular 2026-07-28 and a final one AT maturity, not a dropped/clamped step.
        assertThat(schedule).hasSize(2);
        assertThat(schedule.get(0).date()).isEqualTo(LocalDate.of(2026, 7, 28));
        assertThat(schedule.get(1).date()).isEqualTo(LocalDate.of(2027, 1, 27));
        assertThat(schedule.get(1).status()).isEqualTo("UPCOMING");
    }

    @Test
    void shouldNotAccrueFinalCouponAtMaturity_avoidingDoubleCountWithReceived() {
        // Off-by-day bond (issue 2026-01-28, maturity 2027-01-27): AT maturity the final coupon is PAID with the
        // principal, so accrued must be ZERO — else the detail breakdown counts it twice (accrued + received).
        BondCouponService.CouponAccrual acc = service.accrued(new BigDecimal("36.50"), CouponFrequency.SEMI_ANNUAL,
                LocalDate.of(2026, 1, 28), LocalDate.of(2027, 1, 27), LocalDate.of(2027, 1, 27));
        assertThat(acc.accruedPer100()).isEqualByComparingTo("0");
        assertThat(acc.dailyAccrualPer100()).isEqualByComparingTo("0");

        // And couponsPaid AT maturity counts the final coupon exactly once (2: the regular + the maturity coupon).
        BondCouponService.CouponsPaid paid = service.couponsPaid(new BigDecimal("36.50"), CouponFrequency.SEMI_ANNUAL,
                LocalDate.of(2026, 1, 28), LocalDate.of(2027, 1, 27), LocalDate.of(2026, 1, 28), LocalDate.of(2027, 1, 27));
        assertThat(paid.count()).isEqualTo(2);
    }

    @Test
    void shouldConvertPer100ToFullPositionTry() {
        // Arrange + Act: 1.00 per 100 nominal on 10 000 nominal = 100 TRY.
        BigDecimal full = service.per100ToTry(new BigDecimal("1.00"), new BigDecimal("10000"));

        // Assert
        assertThat(full).isEqualByComparingTo("100");
    }
}
