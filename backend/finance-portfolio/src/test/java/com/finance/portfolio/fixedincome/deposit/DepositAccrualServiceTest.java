package com.finance.portfolio.fixedincome.deposit;

import com.finance.portfolio.config.PortfolioProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link DepositAccrualService}: SIMPLE interest (act/365) net of withholding tax (stopaj), the
 * gross/stopaj/net breakdown, the freeze-at-maturity and pre-start behaviour, and the column-overflow clamp.
 * Uses the default {@link PortfolioProperties} stopaj rate (15%).
 */
class DepositAccrualServiceTest {

    private final DepositAccrualService service = new DepositAccrualService(new PortfolioProperties());

    @Test
    void shouldAccrueSimpleInterestNetOfStopajOverExactlyOneYear() {
        // Arrange: 100 000 at 45% for one year. Gross simple interest = 45 000; stopaj 15% = 6 750;
        // net interest = 38 250; net value = 138 250.
        LocalDate start = LocalDate.of(2025, 1, 1);
        DepositHolding holding = deposit(new BigDecimal("100000"), new BigDecimal("45"),
                start, start.plusDays(365));

        // Act
        DepositAccrualService.DepositAccrual acc = service.breakdown(holding, start.plusDays(365));

        // Assert
        assertThat(acc.grossInterest()).isEqualByComparingTo("45000");
        assertThat(acc.withholdingTax()).isEqualByComparingTo("6750");
        assertThat(acc.netInterest()).isEqualByComparingTo("38250");
        assertThat(acc.netValue()).isEqualByComparingTo("138250");
        assertThat(service.accruedValue(holding, start.plusDays(365))).isEqualByComparingTo("138250");
    }

    @Test
    void shouldReturnPrincipalWhenAsOfEqualsStartDate() {
        // Arrange
        LocalDate start = LocalDate.of(2025, 1, 1);
        DepositHolding holding = deposit(new BigDecimal("100000"), new BigDecimal("45"),
                start, start.plusDays(365));

        // Act
        BigDecimal accrued = service.accruedValue(holding, start);

        // Assert
        assertThat(accrued).isEqualByComparingTo("100000");
    }

    @ParameterizedTest
    @CsvSource({
            "-1",
            "-30",
    })
    void shouldReturnPrincipalWhenAsOfBeforeStartDate(long offsetDays) {
        // Arrange
        LocalDate start = LocalDate.of(2025, 1, 1);
        DepositHolding holding = deposit(new BigDecimal("100000"), new BigDecimal("45"),
                start, start.plusDays(365));

        // Act
        BigDecimal accrued = service.accruedValue(holding, start.plusDays(offsetDays));

        // Assert
        assertThat(accrued).isEqualByComparingTo("100000");
    }

    @ParameterizedTest
    @CsvSource({
            "1",
            "60",
            "365",
    })
    void shouldFreezeValueAtMaturityForAnyLaterAsOf(long daysPastMaturity) {
        // Arrange
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate maturity = start.plusDays(365);
        DepositHolding holding = deposit(new BigDecimal("100000"), new BigDecimal("45"), start, maturity);
        BigDecimal maturityValue = service.accruedValue(holding, maturity);

        // Act
        BigDecimal afterMaturity = service.accruedValue(holding, maturity.plusDays(daysPastMaturity));

        // Assert
        assertThat(afterMaturity).isEqualByComparingTo(maturityValue);
    }

    @Test
    void shouldAccrueInHoldingCurrencyWithoutFxConversion() {
        // Arrange: ~5% on 1000 USD units for a year, net of 15% stopaj → 1000 + 50 × 0.85 = 1042.50.
        LocalDate start = LocalDate.of(2025, 1, 1);
        DepositHolding holding = depositInCurrency("USD", new BigDecimal("1000"), new BigDecimal("5"),
                start, start.plusDays(365));

        // Act
        BigDecimal accrued = service.accruedValue(holding, start.plusDays(365));

        // Assert: no TRY blow-up from any hidden FX multiply.
        assertThat(accrued).isCloseTo(new BigDecimal("1042.50"), within(new BigDecimal("0.01")));
    }

    @Test
    void shouldReturnFrozenClosedValueWhenHoldingClosed() {
        // Arrange
        LocalDate start = LocalDate.of(2025, 1, 1);
        DepositHolding holding = deposit(new BigDecimal("100000"), new BigDecimal("45"),
                start, start.plusDays(365));
        holding.close(start.plusDays(100), new BigDecimal("112345.67"));

        // Act
        BigDecimal value = service.realizedOrAccruedValue(holding, start.plusDays(300));

        // Assert
        assertThat(value).isEqualByComparingTo("112345.67");
    }

    @Test
    void shouldReturnLiveAccruedValueWhenHoldingActive() {
        // Arrange
        LocalDate start = LocalDate.of(2025, 1, 1);
        DepositHolding holding = deposit(new BigDecimal("100000"), new BigDecimal("45"),
                start, start.plusDays(365));

        // Act
        BigDecimal value = service.realizedOrAccruedValue(holding, start);

        // Assert
        assertThat(value).isEqualByComparingTo("100000");
    }

    @Test
    void shouldClampNetValueToColumnMax_whenInputWouldOverflow() {
        // Arrange: an extreme (validator would normally reject this) deposit whose net interest blows far past the
        // numeric(23,8) column max. accruedValue is the last line of defence: it must saturate at the column
        // ceiling rather than emit a value that throws a numeric-overflow on persist.
        LocalDate start = LocalDate.of(2000, 1, 1);
        DepositHolding holding = deposit(new BigDecimal("1000000000000"), new BigDecimal("100000"),
                start, start.plusYears(20));

        // Act
        BigDecimal accrued = service.accruedValue(holding, start.plusYears(20));

        // Assert: clamped exactly at the 15-integer-digit numeric(23,8) ceiling, never above it.
        assertThat(accrued).isEqualByComparingTo("999999999999999.99999999");
    }

    @Test
    void shouldApplyPerHoldingWithholdingRate_overridingTheDefault() {
        // Arrange: 100 000 at 45% for a year, but THIS deposit carries a 10% stopaj (not the configured 15%).
        // Gross 45 000; stopaj 10% = 4 500; net interest 40 500; net value 140 500.
        LocalDate start = LocalDate.of(2025, 1, 1);
        DepositHolding holding = DepositHolding.builder()
                .currency("TRY").principal(new BigDecimal("100000")).annualRate(new BigDecimal("45"))
                .startDate(start).maturityDate(start.plusDays(365))
                .withholdingRate(new BigDecimal("0.1000"))
                .build();

        // Act
        DepositAccrualService.DepositAccrual acc = service.breakdown(holding, start.plusDays(365));

        // Assert: the per-deposit 10% stopaj is applied, not the default 15%.
        assertThat(service.effectiveWithholdingRate(holding)).isEqualByComparingTo("0.1000");
        assertThat(acc.withholdingTax()).isEqualByComparingTo("4500");
        assertThat(acc.netInterest()).isEqualByComparingTo("40500");
        assertThat(acc.netValue()).isEqualByComparingTo("140500");
    }

    @Test
    void shouldFallBackToDefaultWithholding_whenHoldingRateIsNull() {
        // Arrange: no per-deposit stopaj → the configured 15% default applies (gross 45 000 → stopaj 6 750).
        LocalDate start = LocalDate.of(2025, 1, 1);
        DepositHolding holding = deposit(new BigDecimal("100000"), new BigDecimal("45"), start, start.plusDays(365));

        // Act + Assert
        assertThat(service.effectiveWithholdingRate(holding)).isEqualByComparingTo(service.withholdingRate());
        assertThat(service.breakdown(holding, start.plusDays(365)).withholdingTax()).isEqualByComparingTo("6750");
    }

    private DepositHolding deposit(BigDecimal principal, BigDecimal annualRate,
                                   LocalDate start, LocalDate maturity) {
        return depositInCurrency("TRY", principal, annualRate, start, maturity);
    }

    private DepositHolding depositInCurrency(String currency, BigDecimal principal, BigDecimal annualRate,
                                             LocalDate start, LocalDate maturity) {
        return DepositHolding.builder()
                .currency(currency)
                .principal(principal)
                .annualRate(annualRate)
                .startDate(start)
                .maturityDate(maturity)
                .build();
    }
}
