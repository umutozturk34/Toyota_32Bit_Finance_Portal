package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioSeriesProviderTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioDailySnapshotRepository dailySnapshotRepository;
    @Mock private com.finance.portfolio.service.performance.PortfolioPerformanceService portfolioPerformanceService;

    @InjectMocks
    private PortfolioSeriesProvider provider;

    private PortfolioDailySnapshot snapshot(LocalDate date, BigDecimal value) {
        return PortfolioDailySnapshot.builder()
                .snapshotDate(date)
                .totalValueTry(value)
                .build();
    }

    @Test
    void shouldRaiseNotFound_whenPortfolioMissing() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(portfolioRepository.findByIdAndUserSub(7L, "u")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.dailyValueSeries(7L, "u", from, to))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.portfolio.notFound");
    }

    @Test
    void shouldMapSnapshotsToHistoryPoints_whenSnapshotsExist() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(portfolioRepository.findByIdAndUserSub(7L, "u"))
                .thenReturn(Optional.of(new Portfolio()));
        when(dailySnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(7L, from, to))
                .thenReturn(List.of(
                        snapshot(LocalDate.of(2024, 2, 1), new BigDecimal("100")),
                        snapshot(LocalDate.of(2024, 3, 1), new BigDecimal("150"))));

        List<HistoryPoint> series = provider.dailyValueSeries(7L, "u", from, to);

        assertThat(series).hasSize(2);
        assertThat(series.get(0).date()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(series.get(0).value()).isEqualByComparingTo("100");
        assertThat(series.get(1).value()).isEqualByComparingTo("150");
    }

    @Test
    void shouldFilterOutSnapshotsWithNullFields_whenMappingResults() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(portfolioRepository.findByIdAndUserSub(7L, "u"))
                .thenReturn(Optional.of(new Portfolio()));
        when(dailySnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(7L, from, to))
                .thenReturn(List.of(
                        snapshot(null, new BigDecimal("100")),
                        snapshot(LocalDate.of(2024, 3, 1), null),
                        snapshot(LocalDate.of(2024, 4, 1), new BigDecimal("200"))));

        List<HistoryPoint> series = provider.dailyValueSeries(7L, "u", from, to);

        assertThat(series).hasSize(1);
        assertThat(series.get(0).date()).isEqualTo(LocalDate.of(2024, 4, 1));
    }

    @Test
    void shouldReturnEmptySeries_whenNoSnapshotsFound() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(portfolioRepository.findByIdAndUserSub(7L, "u"))
                .thenReturn(Optional.of(new Portfolio()));
        when(dailySnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(7L, from, to))
                .thenReturn(List.of());

        List<HistoryPoint> series = provider.dailyValueSeries(7L, "u", from, to);

        assertThat(series).isEmpty();
    }

    @Test
    void shouldMapTwrIndexToHistoryPoints_whenReturnIndexRequested() {
        // dailyReturnIndexSeries delegates the time-weighted-return math to PortfolioPerformanceService and just
        // maps the resulting index to history points (the TWR chaining itself is unit-tested there). TWR is the
        // contribution-neutral compare line: it intentionally CAN read above 100 on a loss (the money-weighted
        // card is the one that reflects the actual wallet) — so no "loss must read below 100" assertion here.
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        LocalDate d1 = LocalDate.of(2024, 2, 1);
        LocalDate d2 = LocalDate.of(2024, 2, 2);
        when(portfolioRepository.findByIdAndUserSub(7L, "u"))
                .thenReturn(Optional.of(new Portfolio()));
        when(dailySnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(7L, from, to))
                .thenReturn(List.of(snapshot(d1, new BigDecimal("100")), snapshot(d2, new BigDecimal("110"))));
        java.util.LinkedHashMap<LocalDate, BigDecimal> twr = new java.util.LinkedHashMap<>();
        twr.put(d1, new BigDecimal("100"));
        twr.put(d2, new BigDecimal("110.5"));
        when(portfolioPerformanceService.twrIndexSeries(org.mockito.ArgumentMatchers.anyList())).thenReturn(twr);

        List<HistoryPoint> series = provider.dailyReturnIndexSeries(7L, "u", from, to);

        assertThat(series).hasSize(2);
        assertThat(series.get(0).date()).isEqualTo(d1);
        assertThat(series.get(0).value()).isEqualByComparingTo("100");
        assertThat(series.get(1).value()).isEqualByComparingTo("110.5");
    }

    @Test
    void shouldRaiseNotFound_whenPortfolioMissingForReturnIndex() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(portfolioRepository.findByIdAndUserSub(7L, "u")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.dailyReturnIndexSeries(7L, "u", from, to))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.portfolio.notFound");
    }

    private PortfolioDailySnapshot pnlSnapshot(LocalDate date, BigDecimal totalPnlTry) {
        return PortfolioDailySnapshot.builder()
                .snapshotDate(date)
                .totalPnlTry(totalPnlTry)
                .build();
    }

    @Test
    void shouldMapCumulativePnlToHistoryPoints_whenPnlSeriesRequested() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(portfolioRepository.findByIdAndUserSub(7L, "u"))
                .thenReturn(Optional.of(new Portfolio()));
        when(dailySnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(7L, from, to))
                .thenReturn(List.of(
                        pnlSnapshot(LocalDate.of(2024, 2, 1), new BigDecimal("0")),
                        pnlSnapshot(LocalDate.of(2024, 3, 1), new BigDecimal("450")),
                        pnlSnapshot(LocalDate.of(2024, 4, 1), new BigDecimal("-120"))));

        List<HistoryPoint> series = provider.dailyPnlSeries(7L, "u", from, to);

        assertThat(series).hasSize(3);
        assertThat(series.get(0).value()).isEqualByComparingTo("0");
        assertThat(series.get(1).value()).isEqualByComparingTo("450");
        assertThat(series.get(2).value()).isEqualByComparingTo("-120");
    }

    @Test
    void shouldRaiseNotFound_whenPortfolioMissingForPnl() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(portfolioRepository.findByIdAndUserSub(7L, "u")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.dailyPnlSeries(7L, "u", from, to))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.portfolio.notFound");
    }

    @Test
    void shouldAttachPerCurrencyPnl_toPnlSeriesPoints() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        LocalDate d = LocalDate.of(2024, 3, 1);
        when(portfolioRepository.findByIdAndUserSub(7L, "u")).thenReturn(Optional.of(new Portfolio()));
        when(dailySnapshotRepository.findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(7L, from, to))
                .thenReturn(List.of(pnlSnapshot(d, new BigDecimal("450"))));
        when(portfolioPerformanceService.dailyPnlByCcy(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(java.util.Map.of(d,
                        java.util.Map.of("USD", new BigDecimal("12.5"), "EUR", new BigDecimal("11"))));

        List<HistoryPoint> series = provider.dailyPnlSeries(7L, "u", from, to);

        assertThat(series).hasSize(1);
        // TRY P&L stays the stored scalar; the per-currency frame rides along so the compare overlay shows the
        // cost-based foreign-currency P&L instead of a single-rate conversion of this TRY value.
        assertThat(series.get(0).value()).isEqualByComparingTo("450");
        assertThat(series.get(0).pnlByCcy()).containsEntry("USD", new BigDecimal("12.5"));
        assertThat(series.get(0).pnlByCcy()).containsEntry("EUR", new BigDecimal("11"));
    }
}
