package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.response.PositionResponse;
import com.finance.backend.mapper.PortfolioResponseMapper;
import com.finance.backend.model.AssetType;
import com.finance.backend.model.PortfolioPosition;
import com.finance.backend.repository.PortfolioPositionRepository;
import com.finance.backend.repository.PortfolioRepository;
import com.finance.backend.repository.PortfolioTransactionRepository;
import com.finance.backend.repository.UserWalletRepository;
import com.finance.backend.service.support.CountingAssetPricingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioSummaryServiceTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioPositionRepository positionRepository;
    @Mock private PortfolioTransactionRepository transactionRepository;
    @Mock private UserWalletRepository walletRepository;
    @Mock private PortfolioResponseMapper responseMapper;

    private CountingAssetPricingPort counting;
    private PortfolioSummaryService service;

    @BeforeEach
    void setUp() {
        counting = new CountingAssetPricingPort();
        service = new PortfolioSummaryService(
                counting,
                portfolioRepository,
                positionRepository,
                transactionRepository,
                walletRepository,
                responseMapper,
                new AppProperties());
    }

    @Test
    void getPositionsIssuesExactlyOneBundlesBatchCall() {
        counting.seedPrice("CRYPTO", "bitcoin", new BigDecimal("2500000"));
        counting.seedPrice("STOCK", "THYAO.IS", new BigDecimal("50"));
        counting.seedPrice("FUND", "AAK", new BigDecimal("110"));
        counting.seedSellPrice("CRYPTO", "bitcoin", new BigDecimal("2490000"));
        counting.seedSellPrice("STOCK", "THYAO.IS", new BigDecimal("49.9"));
        counting.seedSellPrice("FUND", "AAK", new BigDecimal("109.5"));

        when(positionRepository.findByPortfolioIdAndQuantityGreaterThan(1L, BigDecimal.ZERO))
                .thenReturn(List.of(
                        stubPosition(AssetType.CRYPTO, "bitcoin", new BigDecimal("1"), new BigDecimal("2400000")),
                        stubPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("100"), new BigDecimal("4000")),
                        stubPosition(AssetType.FUND, "AAK", new BigDecimal("50"), new BigDecimal("5000"))));
        when(responseMapper.toPositionResponse(
                any(PortfolioPosition.class),
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        List<PositionResponse> result = service.getPositions(1L);

        assertThat(result).hasSize(3);
        assertThat(counting.batchBundlesCalls()).isEqualTo(1);
        assertThat(counting.priceCalls()).isEqualTo(0);
        assertThat(counting.sellPriceCalls()).isEqualTo(0);
        assertThat(counting.metaCalls()).isEqualTo(0);
    }

    private PortfolioPosition stubPosition(AssetType type, String code, BigDecimal qty, BigDecimal totalCost) {
        PortfolioPosition pos = new PortfolioPosition();
        pos.setAssetType(type);
        pos.setAssetCode(code);
        pos.setQuantity(qty);
        pos.setTotalCostTry(totalCost);
        return pos;
    }
}
