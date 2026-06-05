package com.finance.portfolio.service.summary;

import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyAggregationServiceTest {

    @Mock private PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    @Mock private PortfolioDailySnapshotRepository portfolioSnapshotRepository;
    @Mock private DerivativePositionRepository derivativePositionRepository;

    private DailyAggregationService service;

    @BeforeEach
    void setUp() {
        service = new DailyAggregationService(
                assetSnapshotRepository, portfolioSnapshotRepository, derivativePositionRepository);
    }

    private DerivativePosition viop(String symbol, LocalDate closeDate) {
        ViopContract c = ViopContract.builder()
                .symbol(symbol).kind(ViopContractKind.FUTURE)
                .contractSize(BigDecimal.ONE).currency("TRY").active(true).build();
        return DerivativePosition.builder()
                .viopContract(c).direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.of(2026, 4, 1)).entryPrice(new BigDecimal("100"))
                .quantityLot(BigDecimal.ONE)
                .closeDate(closeDate)
                .closePrice(closeDate != null ? new BigDecimal("110") : null)
                .build();
    }

    private PortfolioPosition spot(String code, LocalDate exitDate) {
        return PortfolioPosition.builder()
                .assetType(AssetType.STOCK).assetCode(code)
                .quantity(BigDecimal.ONE).entryPrice(BigDecimal.TEN)
                .entryDate(LocalDateTime.of(2026, 4, 1, 0, 0))
                .exitDate(exitDate != null ? exitDate.atStartOfDay() : null)
                .exitPrice(exitDate != null ? BigDecimal.TEN : null)
                .build();
    }

    @Test
    void shouldIncludeOpenAndClosedTodaySpot_butExcludeOlderClosed_inLiveKeys() {
        // Arrange: an open lot, a lot sold TODAY (still moved today), and a lot sold YESTERDAY (stale).
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of());
        List<PortfolioPosition> spot = List.of(
                spot("OPEN", null),
                spot("SOLDTODAY", LocalDate.now()),
                spot("SOLDYESTERDAY", LocalDate.now().minusDays(1)));

        // Act
        Set<String> keys = service.liveOpenAssetKeys(1L, spot);

        // Assert: open + sold-today count toward today's daily (so a full sell books its day-move, not 0);
        // an older sale stays excluded (no phantom daily).
        assertThat(keys).contains("STOCK|OPEN", "STOCK|SOLDTODAY");
        assertThat(keys).doesNotContain("STOCK|SOLDYESTERDAY");
    }

    @Test
    void shouldIncludeOpenAndClosedTodayViop_butExcludeOlderClosed_inLiveKeys() {
        // Arrange: an open leg, a leg closed TODAY (still moved today), and a leg closed YESTERDAY (stale).
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(
                viop("OPEN", null),
                viop("CLOSEDTODAY", LocalDate.now()),
                viop("CLOSEDYESTERDAY", LocalDate.now().minusDays(1))));

        // Act
        Set<String> keys = service.liveOpenAssetKeys(1L, List.of());

        // Assert: open + closed-today count toward today's daily; older closures stay excluded (no phantom daily).
        assertThat(keys).contains("VIOP|OPEN", "VIOP|CLOSEDTODAY");
        assertThat(keys).doesNotContain("VIOP|CLOSEDYESTERDAY");
    }
}
