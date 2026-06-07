package com.finance.notification.reports.service;

import com.finance.notification.reports.dto.PerformanceSeriesPoint;
import com.finance.notification.reports.model.ReportPalette;
import com.finance.notification.reports.view.AllocationViewItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ReportSvgServiceTest {

    private ReportSvgService service;

    @BeforeEach
    void setUp() {
        service = new ReportSvgService();
    }

    @Test
    void should_returnPlaceholderDiv_when_performancePointsAreNull() {
        String result = service.performanceLineChart(null, ReportPalette.DARK, Locale.ENGLISH, "");

        assertThat(result).startsWith("<div");
        assertThat(result).contains("—");
    }

    @Test
    void should_returnPlaceholderDiv_when_performancePointsHasSinglePoint() {
        List<PerformanceSeriesPoint> points = List.of(
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 1, 1, 0, 0), 100d));

        String result = service.performanceLineChart(points, ReportPalette.DARK, Locale.ENGLISH, "");

        assertThat(result).startsWith("<div");
    }

    @Test
    void should_renderSvgWithLineAndArea_when_performanceHasMultiplePoints() {
        List<PerformanceSeriesPoint> points = List.of(
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 1, 1, 0, 0), 100d),
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 1, 2, 0, 0), 110d),
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 1, 3, 0, 0), 105d));

        String result = service.performanceLineChart(points, ReportPalette.DARK, Locale.ENGLISH, "");

        assertThat(result).startsWith("<svg");
        assertThat(result).endsWith("</svg>");
        assertThat(result).contains("<path");
        assertThat(result).contains("linearGradient");
        assertThat(result).contains("perfFill");
    }

    @Test
    void should_useSuccessColor_when_performanceTrendIsPositive() {
        List<PerformanceSeriesPoint> points = List.of(
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 1, 1, 0, 0), 100d),
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 1, 2, 0, 0), 200d));

        String result = service.performanceLineChart(points, ReportPalette.DARK, Locale.ENGLISH, "");

        assertThat(result).contains(ReportPalette.DARK.successFg());
    }

    @Test
    void should_useDangerColor_when_performanceTrendIsNegative() {
        List<PerformanceSeriesPoint> points = List.of(
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 1, 1, 0, 0), 200d),
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 1, 2, 0, 0), 100d));

        String result = service.performanceLineChart(points, ReportPalette.DARK, Locale.ENGLISH, "");

        assertThat(result).contains(ReportPalette.DARK.dangerFg());
    }

    @Test
    void should_prefixCurrencySymbolOnValueTicks_when_symbolProvided() {
        // Arrange
        List<PerformanceSeriesPoint> points = List.of(
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 1, 1, 0, 0), 1_200_000d),
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 1, 2, 0, 0), 1_500_000d));

        // Act
        String result = service.performanceLineChart(points, ReportPalette.DARK, Locale.ENGLISH, "$");

        // Assert
        assertThat(result).contains("$1.");
        assertThat(result).contains("M</text>");
    }

    @Test
    void should_renderMultiYearXLabels_when_spanCrossesYear() {
        List<PerformanceSeriesPoint> points = List.of(
                new PerformanceSeriesPoint(LocalDateTime.of(2025, 6, 1, 0, 0), 100d),
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 6, 1, 0, 0), 200d));

        String result = service.performanceLineChart(points, ReportPalette.LIGHT, Locale.ENGLISH, "");

        assertThat(result).contains("<text");
        assertThat(result).contains("25");
    }

    @Test
    void should_notRepeatXLabel_when_allTicksResolveToSameDay() {
        // A span confined to a single calendar day makes all 6 evenly-spaced ticks format to the same
        // "dd MMM" label; the axis must render it once, not six times.
        List<PerformanceSeriesPoint> points = List.of(
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 1, 1, 0, 0), 100d),
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 1, 1, 23, 0), 110d));

        String result = service.performanceLineChart(points, ReportPalette.DARK, Locale.ENGLISH, "");

        int occurrences = result.split("01 Jan", -1).length - 1;
        assertThat(occurrences).isEqualTo(1);
    }

    @Test
    void should_handleFlatSeries_when_allPointsShareSameValue() {
        List<PerformanceSeriesPoint> points = List.of(
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 1, 1, 0, 0), 50d),
                new PerformanceSeriesPoint(LocalDateTime.of(2026, 1, 2, 0, 0), 50d));

        String result = service.performanceLineChart(points, ReportPalette.DARK, Locale.ENGLISH, "");

        assertThat(result).startsWith("<svg");
        assertThat(result).contains("<path");
    }

    @Test
    void should_returnPlaceholder_when_allocationItemsAreNull() {
        String result = service.allocationDonut(null, ReportPalette.DARK);

        assertThat(result).startsWith("<div");
    }

    @Test
    void should_returnPlaceholder_when_allocationItemsAreEmpty() {
        String result = service.allocationDonut(List.of(), ReportPalette.DARK);

        assertThat(result).startsWith("<div");
    }

    @Test
    void should_returnPlaceholder_when_allocationTotalIsZero() {
        List<AllocationViewItem> items = List.of(
                new AllocationViewItem("A", BigDecimal.ZERO, BigDecimal.ZERO, "#fff"));

        String result = service.allocationDonut(items, ReportPalette.DARK);

        assertThat(result).startsWith("<div");
    }

    @Test
    void should_renderFullCircleAndHundredPercentLabel_when_singleNonZeroAllocation() {
        List<AllocationViewItem> items = List.of(
                new AllocationViewItem("Stocks", new BigDecimal("1000"), new BigDecimal("100"), "#abcdef"));

        String result = service.allocationDonut(items, ReportPalette.DARK);

        assertThat(result).startsWith("<svg");
        assertThat(result).contains("<circle");
        assertThat(result).contains("#abcdef");
        assertThat(result).contains("100%");
    }

    @Test
    void should_renderDonutSlices_when_multipleAllocationsProvided() {
        List<AllocationViewItem> items = List.of(
                new AllocationViewItem("A", new BigDecimal("60"), new BigDecimal("60"), "#111111"),
                new AllocationViewItem("B", new BigDecimal("40"), new BigDecimal("40"), "#222222"));

        String result = service.allocationDonut(items, ReportPalette.DARK);

        assertThat(result).startsWith("<svg");
        assertThat(result).contains("#111111");
        assertThat(result).contains("#222222");
        assertThat(result).contains("<path");
    }

    @Test
    void should_skipSmallSliceLabels_when_fractionBelowSixPercent() {
        List<AllocationViewItem> items = List.of(
                new AllocationViewItem("Big", new BigDecimal("97"), new BigDecimal("97"), "#aaaaaa"),
                new AllocationViewItem("Small", new BigDecimal("3"), new BigDecimal("3"), "#bbbbbb"));

        String result = service.allocationDonut(items, ReportPalette.DARK);

        assertThat(result).contains("97,0%");
        assertThat(result).doesNotContain(">3%<");
    }

    @Test
    void should_skipNullValueAllocation_when_renderingDonut() {
        List<AllocationViewItem> items = List.of(
                new AllocationViewItem("Real", new BigDecimal("100"), new BigDecimal("100"), "#cccccc"),
                new AllocationViewItem("Empty", null, null, "#dddddd"));

        String result = service.allocationDonut(items, ReportPalette.DARK);

        assertThat(result).contains("#cccccc");
        assertThat(result).contains("100%");
    }

    @Test
    void should_renderFullRing_when_singleAllocationIsHundredPercent() {
        List<AllocationViewItem> items = List.of(
                new AllocationViewItem("Crypto", new BigDecimal("100"), new BigDecimal("100"), "#5e6ad2"));

        String result = service.allocationDonut(items, ReportPalette.DARK);

        // A 360-degree arc is degenerate, so the full ring must be drawn as a stroked circle.
        assertThat(result).contains("<circle");
        assertThat(result).contains("#5e6ad2");
        assertThat(result).contains("100%");
    }

    @Test
    void should_renderTinySliceAsVisibleArc_withoutOnDonutLabel_andKeepLabelsInsideViewBox() {
        // Arrange: the real 5-bucket allocation from the report, including a 0.7% "Fon" slice — the case
        // where one worries a tiny slice or its label gets crushed/hidden behind the donut in the PDF.
        List<AllocationViewItem> items = List.of(
                new AllocationViewItem("Kripto", new BigDecimal("4690"), new BigDecimal("46.9"), "#5e6ad2"),
                new AllocationViewItem("Nakit",  new BigDecimal("2900"), new BigDecimal("29.0"), "#7c5cfc"),
                new AllocationViewItem("Hisse",  new BigDecimal("1430"), new BigDecimal("14.3"), "#c084fc"),
                new AllocationViewItem("Doviz",  new BigDecimal("920"),  new BigDecimal("9.2"),  "#22d3ee"),
                new AllocationViewItem("Fon",    new BigDecimal("70"),   new BigDecimal("0.7"),  "#10b981"));

        // Act
        String svg = service.allocationDonut(items, ReportPalette.DARK);

        // Assert: the tiny 0.7% slice IS drawn (its fill color appears) — it is floored to a min-visible
        // arc, never hidden — and it carries NO on-donut label (sub-6% rule), so it cannot collide with a
        // neighbour; its exact share is shown in the legend table instead. The >=6% slices keep their labels.
        assertThat(svg).contains("#10b981");
        assertThat(svg).doesNotContain("0,7%");
        assertThat(svg).contains("46,9%").contains("9,2%");

        // And every on-donut label sits inside the 0..180 viewBox — no off-canvas / clipped text.
        var matcher = java.util.regex.Pattern.compile("<text x=\"([0-9.]+)\" y=\"([0-9.]+)\"").matcher(svg);
        int labels = 0;
        while (matcher.find()) {
            labels++;
            assertThat(Double.parseDouble(matcher.group(1))).isBetween(0.0, 180.0);
            assertThat(Double.parseDouble(matcher.group(2))).isBetween(0.0, 180.0);
        }
        assertThat(labels).isEqualTo(4);
    }
}
