package com.finance.backend.service;

import com.finance.backend.config.PortfolioProperties;
import com.finance.backend.model.*;
import com.finance.backend.model.value.MoneyTRY;
import com.finance.backend.repository.PortfolioPositionRepository;
import com.finance.backend.repository.UserWalletRepository;
import com.finance.backend.service.support.CountingAssetPricingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotCalculationServiceTest {

    @Mock(answer = Answers.CALLS_REAL_METHODS) private AssetPricingPort pricingPort;
    @Mock private PortfolioPositionRepository positionRepository;
    @Mock private UserWalletRepository walletRepository;

    private SnapshotCalculationService service;

    @BeforeEach
    void setUp() {
        service = new SnapshotCalculationService(pricingPort, positionRepository, walletRepository, new PortfolioProperties());
    }

    @Test
    void assetSnapshotCalculatesPnlFromCurrentPriceVsTotalCost() {
        PortfolioPosition pos = stubPosition(AssetType.CRYPTO, "bitcoin",
                new BigDecimal("0.50000000"), new BigDecimal("1250000.0000"));
        when(pricingPort.getPriceTry(MarketType.CRYPTO,"bitcoin")).thenReturn(new BigDecimal("2600000.0000"));
        LocalDateTime timestamp = LocalDateTime.of(2026, 4, 10, 23, 0);

        PortfolioAssetDailySnapshot snapshot = service.buildAssetSnapshot(1L, pos, timestamp);

        assertThat(snapshot.getMarketValueTry()).isEqualByComparingTo(new BigDecimal("1300000.0000"));
        assertThat(snapshot.getPnlTry()).isEqualByComparingTo(new BigDecimal("50000.0000"));
        assertThat(snapshot.getUnitPriceTry()).isEqualByComparingTo(new BigDecimal("2600000.0000"));
        assertThat(snapshot.getSnapshotDate()).isEqualTo(timestamp.toLocalDate());
        assertThat(snapshot.getCreatedAt()).isEqualTo(timestamp);
    }

    @Test
    void assetSnapshotWithNullPriceShowsFullLoss() {
        PortfolioPosition pos = stubPosition(AssetType.STOCK, "DELISTED",
                new BigDecimal("100.00000000"), new BigDecimal("5000.0000"));
        when(pricingPort.getPriceTry(MarketType.STOCK,"DELISTED")).thenReturn(null);

        PortfolioAssetDailySnapshot snapshot = service.buildAssetSnapshot(1L, pos, LocalDateTime.now());

        assertThat(snapshot.getUnitPriceTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getMarketValueTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getPnlTry()).isEqualByComparingTo(new BigDecimal("-5000.0000"));
    }

    @Test
    void aggregateSnapshotSumsAllPositionsAndIncludesCash() {
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        when(positionRepository.findByPortfolioIdAndQuantityGreaterThan(1L, BigDecimal.ZERO))
                .thenReturn(List.of(
                        stubPosition(AssetType.CRYPTO, "bitcoin", new BigDecimal("1.00000000"), new BigDecimal("2400000.0000")),
                        stubPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("100.00000000"), new BigDecimal("4000.0000"))));
        when(pricingPort.getPriceTry(MarketType.CRYPTO,"bitcoin")).thenReturn(new BigDecimal("2500000.0000"));
        when(pricingPort.getPriceTry(MarketType.STOCK,"THYAO.IS")).thenReturn(new BigDecimal("50.0000"));
        when(walletRepository.findByPortfolioIdAndCurrency(1L, "TRY"))
                .thenReturn(Optional.of(stubWallet(new BigDecimal("500000.0000"))));

        PortfolioDailySnapshot snapshot = service.buildAggregateSnapshot(portfolio, LocalDateTime.now());

        assertThat(snapshot.getTotalValueTry()).isEqualByComparingTo(new BigDecimal("3005000.0000"));
        assertThat(snapshot.getTotalCostTry()).isEqualByComparingTo(new BigDecimal("2404000.0000"));
        assertThat(snapshot.getCashBalanceTry()).isEqualByComparingTo(new BigDecimal("500000.0000"));
        assertThat(snapshot.getTotalPnlTry()).isEqualByComparingTo(new BigDecimal("101000.0000"));
    }

    @Test
    void aggregateSnapshotPnlPercentRelativeToTotalCost() {
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        when(positionRepository.findByPortfolioIdAndQuantityGreaterThan(1L, BigDecimal.ZERO))
                .thenReturn(List.of(stubPosition(AssetType.FUND, "AAK", new BigDecimal("100.00000000"), new BigDecimal("10000.0000"))));
        when(pricingPort.getPriceTry(MarketType.FUND,"AAK")).thenReturn(new BigDecimal("110.0000"));
        when(walletRepository.findByPortfolioIdAndCurrency(1L, "TRY"))
                .thenReturn(Optional.of(stubWallet(BigDecimal.ZERO)));

        PortfolioDailySnapshot snapshot = service.buildAggregateSnapshot(portfolio, LocalDateTime.now());

        assertThat(snapshot.getPnlPercent()).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    void aggregateSnapshotIssuesExactlyOneBatchPricingCall() {
        CountingAssetPricingPort counting = new CountingAssetPricingPort();
        counting.seedPrice("CRYPTO", "bitcoin", new BigDecimal("2500000.0000"));
        counting.seedPrice("STOCK", "THYAO.IS", new BigDecimal("50.0000"));
        counting.seedPrice("FUND", "AAK", new BigDecimal("110.0000"));

        SnapshotCalculationService countedService = new SnapshotCalculationService(
                counting, positionRepository, walletRepository, new PortfolioProperties());

        Portfolio portfolio = Portfolio.builder().id(1L).build();
        when(positionRepository.findByPortfolioIdAndQuantityGreaterThan(1L, BigDecimal.ZERO))
                .thenReturn(List.of(
                        stubPosition(AssetType.CRYPTO, "bitcoin", new BigDecimal("1.00000000"), new BigDecimal("2400000.0000")),
                        stubPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("100.00000000"), new BigDecimal("4000.0000")),
                        stubPosition(AssetType.FUND, "AAK", new BigDecimal("50.00000000"), new BigDecimal("5000.0000"))));
        when(walletRepository.findByPortfolioIdAndCurrency(1L, "TRY"))
                .thenReturn(Optional.of(stubWallet(BigDecimal.ZERO)));

        countedService.buildAggregateSnapshot(portfolio, LocalDateTime.now());

        assertThat(counting.batchPricesCalls()).isEqualTo(1);
        assertThat(counting.priceCalls()).isEqualTo(0);
    }

    @Test
    void aggregateSnapshotWithZeroCostReturnsZeroPnlPercent() {
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        when(positionRepository.findByPortfolioIdAndQuantityGreaterThan(1L, BigDecimal.ZERO))
                .thenReturn(List.of());
        when(walletRepository.findByPortfolioIdAndCurrency(1L, "TRY"))
                .thenReturn(Optional.of(stubWallet(new BigDecimal("1000000.0000"))));

        PortfolioDailySnapshot snapshot = service.buildAggregateSnapshot(portfolio, LocalDateTime.now());

        assertThat(snapshot.getPnlPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getTotalValueTry()).isEqualByComparingTo(new BigDecimal("1000000.0000"));
    }

    private PortfolioPosition stubPosition(AssetType type, String code, BigDecimal qty, BigDecimal totalCost) {
        return PortfolioPosition.builder()
                .assetType(type)
                .assetCode(code)
                .quantity(qty)
                .totalCostTry(totalCost)
                .build();
    }

    private UserWallet stubWallet(BigDecimal balance) {
        return UserWallet.builder().balance(MoneyTRY.of(balance)).build();
    }
}
