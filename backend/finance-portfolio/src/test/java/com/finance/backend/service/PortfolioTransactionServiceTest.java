package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.request.TransactionRequest;
import com.finance.backend.dto.response.TransactionResponse;
import com.finance.backend.exception.BadRequestException;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.mapper.PortfolioResponseMapper;
import com.finance.backend.model.*;
import com.finance.backend.repository.*;
import com.finance.backend.service.transaction.AmountBasedResolver;
import com.finance.backend.service.transaction.QuantityBasedResolver;
import com.finance.backend.service.transaction.TransactionInputResolverFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioTransactionServiceTest {

    @Mock private AssetPricingPort pricingPort;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioPositionRepository positionRepository;
    @Mock private PortfolioTransactionRepository transactionRepository;
    @Mock private UserWalletRepository walletRepository;
    @Mock private WalletLedgerRepository ledgerRepository;
    @Mock private PortfolioResponseMapper mapper;
    @Mock private PortfolioSnapshotService snapshotService;

    private PortfolioTransactionService service;
    private MockedStatic<TransactionSynchronizationManager> tsmMock;

    private static final String USER_SUB = "user-123";
    private static final Long PORTFOLIO_ID = 1L;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        TransactionInputResolverFactory factory = new TransactionInputResolverFactory(
                List.of(new AmountBasedResolver(appProperties), new QuantityBasedResolver()));
        service = new PortfolioTransactionService(
                pricingPort, portfolioRepository, positionRepository,
                transactionRepository, walletRepository, ledgerRepository,
                mapper, snapshotService, factory, appProperties);
        tsmMock = mockStatic(TransactionSynchronizationManager.class);
    }

    @AfterEach
    void tearDown() {
        tsmMock.close();
    }

    @Test
    void buyCreatesNewPositionWithCorrectWacAndDebitsWallet() {
        Portfolio portfolio = buildPortfolio();
        UserWallet wallet = buildWallet(new BigDecimal("1000000.0000"));
        BigDecimal unitPrice = new BigDecimal("65000.0000");

        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(pricingPort.getPriceTry(MarketType.CRYPTO,"bitcoin")).thenReturn(unitPrice);
        when(walletRepository.findByPortfolioIdAndCurrency(PORTFOLIO_ID, "TRY")).thenReturn(Optional.of(wallet));
        when(positionRepository.findByPortfolioIdAndAssetTypeAndAssetCode(PORTFOLIO_ID, AssetType.CRYPTO, "bitcoin"))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toTransactionResponse(any())).thenReturn(mock(TransactionResponse.class));

        service.execute(USER_SUB, PORTFOLIO_ID,
                new TransactionRequest("CRYPTO", "bitcoin", "BUY", null, new BigDecimal("100000"), null));

        ArgumentCaptor<PortfolioPosition> posCaptor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(positionRepository).save(posCaptor.capture());
        PortfolioPosition saved = posCaptor.getValue();

        BigDecimal expectedQty = new BigDecimal("100000").divide(unitPrice, 8, RoundingMode.DOWN);
        BigDecimal expectedCost = unitPrice.multiply(expectedQty).setScale(4, RoundingMode.HALF_UP);

        assertThat(saved.getQuantity()).isEqualByComparingTo(expectedQty);
        assertThat(saved.getTotalCostTry()).isEqualByComparingTo(expectedCost);
        assertThat(saved.getAverageCostTry()).isEqualByComparingTo(unitPrice);
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("1000000.0000").subtract(expectedCost));
    }

    @Test
    void buyExistingPositionRecalculatesWeightedAverageCost() {
        Portfolio portfolio = buildPortfolio();
        UserWallet wallet = buildWallet(new BigDecimal("500000.0000"));
        PortfolioPosition existing = PortfolioPosition.builder()
                .portfolio(portfolio).assetType(AssetType.CRYPTO).assetCode("bitcoin")
                .quantity(new BigDecimal("2.00000000"))
                .averageCostTry(new BigDecimal("60000.0000"))
                .totalCostTry(new BigDecimal("120000.0000"))
                .build();
        BigDecimal newPrice = new BigDecimal("70000.0000");

        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(pricingPort.getPriceTry(MarketType.CRYPTO,"bitcoin")).thenReturn(newPrice);
        when(walletRepository.findByPortfolioIdAndCurrency(PORTFOLIO_ID, "TRY")).thenReturn(Optional.of(wallet));
        when(positionRepository.findByPortfolioIdAndAssetTypeAndAssetCode(PORTFOLIO_ID, AssetType.CRYPTO, "bitcoin"))
                .thenReturn(Optional.of(existing));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toTransactionResponse(any())).thenReturn(mock(TransactionResponse.class));

        service.execute(USER_SUB, PORTFOLIO_ID,
                new TransactionRequest("CRYPTO", "bitcoin", "BUY", new BigDecimal("1"), null, null));

        assertThat(existing.getQuantity()).isEqualByComparingTo(new BigDecimal("3.00000000"));
        assertThat(existing.getTotalCostTry()).isEqualByComparingTo(new BigDecimal("190000.0000"));
        assertThat(existing.getAverageCostTry()).isEqualByComparingTo(
                new BigDecimal("190000.0000").divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP));
    }

    @Test
    void buyWithInsufficientBalanceThrowsBusinessException() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(buildPortfolio()));
        when(pricingPort.getPriceTry(MarketType.STOCK,"THYAO.IS")).thenReturn(new BigDecimal("45.0000"));
        when(walletRepository.findByPortfolioIdAndCurrency(PORTFOLIO_ID, "TRY"))
                .thenReturn(Optional.of(buildWallet(new BigDecimal("100.0000"))));

        assertThatThrownBy(() -> service.execute(USER_SUB, PORTFOLIO_ID,
                new TransactionRequest("STOCK", "THYAO.IS", "BUY", new BigDecimal("10"), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Alım gücü yetersiz");
    }

    @Test
    void sellCalculatesRealizedPnlAndCreditsWallet() {
        Portfolio portfolio = buildPortfolio();
        UserWallet wallet = buildWallet(new BigDecimal("600000.0000"));
        PortfolioPosition position = buildPosition(portfolio, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("10.00000000"), new BigDecimal("40.0000"), new BigDecimal("400.0000"));

        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(pricingPort.getSellPriceTry(MarketType.STOCK,"THYAO.IS")).thenReturn(new BigDecimal("50.0000"));
        when(walletRepository.findByPortfolioIdAndCurrency(PORTFOLIO_ID, "TRY")).thenReturn(Optional.of(wallet));
        when(positionRepository.findByPortfolioIdAndAssetTypeAndAssetCode(PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS"))
                .thenReturn(Optional.of(position));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toTransactionResponse(any())).thenReturn(mock(TransactionResponse.class));

        service.execute(USER_SUB, PORTFOLIO_ID,
                new TransactionRequest("STOCK", "THYAO.IS", "SELL", new BigDecimal("5"), null, null));

        ArgumentCaptor<PortfolioTransaction> txnCaptor = ArgumentCaptor.forClass(PortfolioTransaction.class);
        verify(transactionRepository).save(txnCaptor.capture());
        assertThat(txnCaptor.getValue().getRealizedPnlTry()).isEqualByComparingTo(new BigDecimal("50.0000"));
        assertThat(portfolio.getRealizedPnlTry()).isEqualByComparingTo(new BigDecimal("50.0000"));
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("600250.0000"));
        assertThat(position.getQuantity()).isEqualByComparingTo(new BigDecimal("5.00000000"));
        assertThat(position.getTotalCostTry()).isEqualByComparingTo(new BigDecimal("200.0000"));
    }

    @Test
    void sellWithFeeReducesProceedsAndPnl() {
        Portfolio portfolio = buildPortfolio();
        UserWallet wallet = buildWallet(new BigDecimal("600000.0000"));
        PortfolioPosition position = buildPosition(portfolio, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("10.00000000"), new BigDecimal("40.0000"), new BigDecimal("400.0000"));

        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(pricingPort.getSellPriceTry(MarketType.STOCK,"THYAO.IS")).thenReturn(new BigDecimal("50.0000"));
        when(walletRepository.findByPortfolioIdAndCurrency(PORTFOLIO_ID, "TRY")).thenReturn(Optional.of(wallet));
        when(positionRepository.findByPortfolioIdAndAssetTypeAndAssetCode(PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS"))
                .thenReturn(Optional.of(position));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toTransactionResponse(any())).thenReturn(mock(TransactionResponse.class));

        service.execute(USER_SUB, PORTFOLIO_ID,
                new TransactionRequest("STOCK", "THYAO.IS", "SELL", new BigDecimal("5"), null, new BigDecimal("2")));

        ArgumentCaptor<PortfolioTransaction> txnCaptor = ArgumentCaptor.forClass(PortfolioTransaction.class);
        verify(transactionRepository).save(txnCaptor.capture());
        assertThat(txnCaptor.getValue().getRealizedPnlTry()).isEqualByComparingTo(new BigDecimal("48.0000"));
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("600248.0000"));
        verify(ledgerRepository, times(2)).save(any(WalletLedger.class));
    }

    @Test
    void sellInsufficientQuantityThrowsBusinessException() {
        Portfolio portfolio = buildPortfolio();
        PortfolioPosition position = buildPosition(portfolio, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("3.00000000"), new BigDecimal("40.0000"), new BigDecimal("120.0000"));

        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(pricingPort.getSellPriceTry(MarketType.STOCK,"THYAO.IS")).thenReturn(new BigDecimal("50.0000"));
        when(walletRepository.findByPortfolioIdAndCurrency(PORTFOLIO_ID, "TRY"))
                .thenReturn(Optional.of(buildWallet(new BigDecimal("1000000.0000"))));
        when(positionRepository.findByPortfolioIdAndAssetTypeAndAssetCode(PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS"))
                .thenReturn(Optional.of(position));

        assertThatThrownBy(() -> service.execute(USER_SUB, PORTFOLIO_ID,
                new TransactionRequest("STOCK", "THYAO.IS", "SELL", new BigDecimal("10"), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Yetersiz miktar");
    }

    @Test
    void priceUnavailableThrowsBadRequestException() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(buildPortfolio()));
        when(pricingPort.getPriceTry(MarketType.CRYPTO,"unknown")).thenReturn(null);

        assertThatThrownBy(() -> service.execute(USER_SUB, PORTFOLIO_ID,
                new TransactionRequest("CRYPTO", "unknown", "BUY", null, new BigDecimal("1000"), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Price not available");
    }

    @Test
    void portfolioNotFoundThrowsResourceNotFound() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(USER_SUB, PORTFOLIO_ID,
                new TransactionRequest("CRYPTO", "bitcoin", "BUY", null, new BigDecimal("1000"), null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sellUsesGetSellPriceTryNotGetPriceTry() {
        Portfolio portfolio = buildPortfolio();
        PortfolioPosition position = buildPosition(portfolio, AssetType.FOREX, "USD",
                new BigDecimal("100.00000000"), new BigDecimal("38.0000"), new BigDecimal("3800.0000"));

        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(pricingPort.getSellPriceTry(MarketType.FOREX,"USD")).thenReturn(new BigDecimal("37.5000"));
        when(walletRepository.findByPortfolioIdAndCurrency(PORTFOLIO_ID, "TRY"))
                .thenReturn(Optional.of(buildWallet(new BigDecimal("1000000.0000"))));
        when(positionRepository.findByPortfolioIdAndAssetTypeAndAssetCode(PORTFOLIO_ID, AssetType.FOREX, "USD"))
                .thenReturn(Optional.of(position));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toTransactionResponse(any())).thenReturn(mock(TransactionResponse.class));

        service.execute(USER_SUB, PORTFOLIO_ID,
                new TransactionRequest("FOREX", "USD", "SELL", null, new BigDecimal("500"), null));

        verify(pricingPort).getSellPriceTry(MarketType.FOREX,"USD");
        verify(pricingPort, never()).getPriceTry(any(), any());
    }

    private Portfolio buildPortfolio() {
        return Portfolio.builder()
                .id(PORTFOLIO_ID).userSub(USER_SUB).name("Test")
                .realizedPnlTry(BigDecimal.ZERO).build();
    }

    private UserWallet buildWallet(BigDecimal balance) {
        return UserWallet.builder()
                .id(1L).currency("TRY")
                .balance(balance).availableBalance(balance).build();
    }

    private PortfolioPosition buildPosition(Portfolio portfolio, AssetType type, String code,
                                            BigDecimal qty, BigDecimal avgCost, BigDecimal totalCost) {
        return PortfolioPosition.builder()
                .portfolio(portfolio).assetType(type).assetCode(code)
                .quantity(qty).averageCostTry(avgCost).totalCostTry(totalCost).build();
    }
}
