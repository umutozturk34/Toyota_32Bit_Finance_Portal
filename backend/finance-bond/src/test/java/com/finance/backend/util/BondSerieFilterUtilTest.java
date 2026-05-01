package com.finance.backend.util;

import com.finance.backend.dto.external.BondSerieDto;
import com.finance.backend.dto.external.BondSnapshotDto;
import com.finance.backend.dto.internal.EvdsBondSerieResponse;
import com.finance.backend.model.Bond;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BondSerieFilterUtilTest {

    @ParameterizedTest
    @CsvSource({
            "TP.TRT130225T14.TL.PY, TRT130225T14",
            "TRT130225T14.TL.PY, TRT130225T14",
            "TP.TRT130225T14, TRT130225T14"
    })
    void extractIsinFromVariousSerieCodeFormats(String serieCode, String expectedIsin) {
        assertThat(BondSerieFilterUtil.extractIsin(serieCode)).isEqualTo(expectedIsin);
    }

    @Test
    void parseDatesExtractsStartAndEndDates() {
        LocalDate[] dates = BondSerieFilterUtil.parseDates(
                "2Y TRT (01.01.2024 01.01.2026)");

        assertThat(dates).hasSize(2);
        assertThat(dates[0]).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(dates[1]).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void parseDatesNullReturnsNull() {
        assertThat(BondSerieFilterUtil.parseDates(null)).isNull();
    }

    @Test
    void parseDatesNoPatternMatchReturnsNull() {
        assertThat(BondSerieFilterUtil.parseDates("Some random text")).isNull();
    }

    @Test
    void parseDatesInvalidDateFormatReturnsNull() {
        assertThat(BondSerieFilterUtil.parseDates("(99.99.9999 01.01.2026)")).isNull();
    }

    @Test
    void filterExcludesOranSuffix() {
        List<EvdsBondSerieResponse> series = List.of(
                new EvdsBondSerieResponse("TP.TRT130225T14.ORAN",
                        "TRT (13.02.2025 13.02.2027)"));

        List<BondSerieDto> result = BondSerieFilterUtil.filter(series);

        assertThat(result).isEmpty();
    }

    @Test
    void filterExcludesNonTrtTrdTrbPrefixes() {
        List<EvdsBondSerieResponse> series = List.of(
                new EvdsBondSerieResponse("TP.XYZ130225T14.TL",
                        "XYZ (13.02.2025 13.02.2027)"));

        List<BondSerieDto> result = BondSerieFilterUtil.filter(series);

        assertThat(result).isEmpty();
    }

    @Test
    void filterExcludesExpiredBonds() {
        List<EvdsBondSerieResponse> series = List.of(
                new EvdsBondSerieResponse("TP.TRT010120T10.TL",
                        "TRT (01.01.2018 01.01.2020)"));

        List<BondSerieDto> result = BondSerieFilterUtil.filter(series);

        assertThat(result).isEmpty();
    }

    @Test
    void filterKeepsValidTrtBond() {
        LocalDate futureDate = LocalDate.now().plusYears(2);
        String endStr = String.format("%02d.%02d.%04d", futureDate.getDayOfMonth(),
                futureDate.getMonthValue(), futureDate.getYear());
        List<EvdsBondSerieResponse> series = List.of(
                new EvdsBondSerieResponse("TP.TRT010124T14.TL",
                        "TRT (01.01.2024 " + endStr + ")"));

        List<BondSerieDto> result = BondSerieFilterUtil.filter(series);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isin()).startsWith("TRT");
    }

    @Test
    void filterDeduplicatesByBaseIsin() {
        LocalDate future = LocalDate.now().plusYears(2);
        String endStr = String.format("%02d.%02d.%04d", future.getDayOfMonth(),
                future.getMonthValue(), future.getYear());
        String name = "TRT (01.01.2024 " + endStr + ")";
        List<EvdsBondSerieResponse> series = List.of(
                new EvdsBondSerieResponse("TP.TRT010124T14.TL", name),
                new EvdsBondSerieResponse("TP.TRT010124T15.TL", name));

        List<BondSerieDto> result = BondSerieFilterUtil.filter(series);

        assertThat(result).hasSize(1);
    }

    @Test
    void filterExcludesWithoutIsinSuffix() {
        LocalDate future = LocalDate.now().plusYears(2);
        String endStr = String.format("%02d.%02d.%04d", future.getDayOfMonth(),
                future.getMonthValue(), future.getYear());
        List<EvdsBondSerieResponse> series = List.of(
                new EvdsBondSerieResponse("TP.TRT010124.TL",
                        "TRT (01.01.2024 " + endStr + ")"));

        List<BondSerieDto> result = BondSerieFilterUtil.filter(series);

        assertThat(result).isEmpty();
    }

    @Test
    void filterExcludesNullSerieCode() {
        List<EvdsBondSerieResponse> series = List.of(
                new EvdsBondSerieResponse(null, "Some name"));

        List<BondSerieDto> result = BondSerieFilterUtil.filter(series);

        assertThat(result).isEmpty();
    }

    @Test
    void sanitizeCouponRateTrbPrefixResetsToZero() {
        Bond bond = Bond.builder().couponRate(new BigDecimal("5.0000")).build();
        BondSnapshotDto dto = new BondSnapshotDto("SERIE1", "TRB010124T12", new BigDecimal("100"),
                new BigDecimal("5.0"), LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1), "TRB Bond");

        BondSerieFilterUtil.sanitizeCouponRate(bond, dto);

        assertThat(bond.getCouponRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sanitizeCouponRateMatchingRemainingDaysResetsToZero() {
        LocalDate maturityEnd = LocalDate.now().plusDays(100);
        Bond bond = Bond.builder().couponRate(new BigDecimal("100")).build();
        BondSnapshotDto dto = new BondSnapshotDto("SERIE1", "TRT010124T12", new BigDecimal("100"),
                new BigDecimal("100"), LocalDate.of(2024, 1, 1), maturityEnd, "TRT Bond");

        BondSerieFilterUtil.sanitizeCouponRate(bond, dto);

        assertThat(bond.getCouponRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sanitizeCouponRateNormalCouponUnchanged() {
        Bond bond = Bond.builder().couponRate(new BigDecimal("12.5000")).build();
        BondSnapshotDto dto = new BondSnapshotDto("SERIE1", "TRT010124T12", new BigDecimal("100"),
                new BigDecimal("12.5"), LocalDate.of(2024, 1, 1), LocalDate.now().plusDays(500), "TRT Bond");

        BondSerieFilterUtil.sanitizeCouponRate(bond, dto);

        assertThat(bond.getCouponRate()).isEqualByComparingTo(new BigDecimal("12.5000"));
    }

    @Test
    void sanitizeCouponRateZeroCouponUnchanged() {
        Bond bond = Bond.builder().couponRate(BigDecimal.ZERO).build();
        BondSnapshotDto dto = new BondSnapshotDto("SERIE1", "TRT010124T12", new BigDecimal("100"),
                BigDecimal.ZERO, LocalDate.of(2024, 1, 1), LocalDate.now().plusDays(200), "TRT Bond");

        BondSerieFilterUtil.sanitizeCouponRate(bond, dto);

        assertThat(bond.getCouponRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sanitizeCouponRateNullCouponUnchanged() {
        Bond bond = Bond.builder().couponRate(null).build();
        BondSnapshotDto dto = new BondSnapshotDto("SERIE1", "TRT010124T12", new BigDecimal("100"),
                null, LocalDate.of(2024, 1, 1), LocalDate.now().plusDays(200), "TRT Bond");

        BondSerieFilterUtil.sanitizeCouponRate(bond, dto);

        assertThat(bond.getCouponRate()).isNull();
    }

    @Test
    void toOranCodeFormatsCorrectly() {
        assertThat(BondSerieFilterUtil.toOranCode("TRT130225T14"))
                .isEqualTo("TP.TRT130225T14.ORAN");
    }

    @Test
    void partitionSplitsIntoCorrectBatches() {
        List<Integer> list = List.of(1, 2, 3, 4, 5);

        List<List<Integer>> batches = BondSerieFilterUtil.partition(list, 2);

        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).containsExactly(1, 2);
        assertThat(batches.get(1)).containsExactly(3, 4);
        assertThat(batches.get(2)).containsExactly(5);
    }

    @Test
    void partitionEmptyListReturnsEmpty() {
        List<List<String>> batches = BondSerieFilterUtil.partition(List.of(), 10);

        assertThat(batches).isEmpty();
    }
}
