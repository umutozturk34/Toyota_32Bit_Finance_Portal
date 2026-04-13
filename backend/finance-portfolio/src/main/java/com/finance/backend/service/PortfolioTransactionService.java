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
import com.finance.backend.service.transaction.ResolvedInput;
import com.finance.backend.service.transaction.TransactionInputResolver;
import com.finance.backend.service.transaction.TransactionInputResolverFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioTransactionService {

    private static final int PRICE_SCALE = 4;

    private final AssetPricingPort pricingPort;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioTransactionRepository transactionRepository;
    private final UserWalletRepository walletRepository;
    private final WalletLedgerRepository ledgerRepository;
    private final PortfolioResponseMapper mapper;
    private final PortfolioSnapshotService snapshotService;
    private final TransactionInputResolverFactory resolverFactory;
    private final AppProperties appProperties;

    @Transactional
    public TransactionResponse execute(String userSub, Long portfolioId, TransactionRequest request) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));

        AssetType assetType;
        TransactionSide side;
        try {
            assetType = AssetType.valueOf(request.assetType());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid asset type: " + request.assetType());
        }
        try {
            side = TransactionSide.valueOf(request.side());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid transaction side: " + request.side());
        }
        BigDecimal fee = request.feeTry() != null ? request.feeTry().setScale(PRICE_SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        BigDecimal unitPrice = side == TransactionSide.SELL
                ? pricingPort.getSellPriceTry(request.assetType(), request.assetCode())
                : pricingPort.getPriceTry(request.assetType(), request.assetCode());
        if (unitPrice == null) {
            throw new BadRequestException("Price not available for " + request.assetType() + ":" + request.assetCode());
        }

        TransactionInputResolver resolver = resolverFactory.getResolver(assetType);
        ResolvedInput resolved = resolver.resolve(request.quantity(), request.amountTry(), unitPrice);
        BigDecimal quantity = resolved.quantity();
        BigDecimal totalCost = resolved.totalCostTry();

        String currency = appProperties.getPortfolio().getDefaultCurrency();
        UserWallet wallet = walletRepository.findByPortfolioIdAndCurrency(portfolioId, currency)
                .orElseThrow(() -> new ResourceNotFoundException(currency + " wallet not found"));

        BigDecimal realizedPnl = BigDecimal.ZERO;
        if (side == TransactionSide.BUY) {
            executeBuy(portfolio, wallet, assetType, request.assetCode(), quantity, totalCost, fee);
        } else {
            realizedPnl = executeSell(portfolio, wallet, assetType, request.assetCode(), quantity, totalCost, fee);
        }

        PortfolioTransaction txn = PortfolioTransaction.builder()
                .portfolio(portfolio)
                .assetType(assetType)
                .assetCode(request.assetCode())
                .side(side)
                .quantity(quantity)
                .unitPriceTry(unitPrice)
                .totalCostTry(totalCost)
                .feeTry(fee)
                .realizedPnlTry(realizedPnl)
                .build();
        txn = transactionRepository.save(txn);

        log.info("{} {} {} @ {} {} (fee={}) for portfolio {}",
                side, quantity, request.assetCode(), unitPrice,
                appProperties.getPortfolio().getDefaultCurrency(), fee, portfolioId);

        final Long pid = portfolioId;
        final AssetType snapshotType = assetType;
        final String snapshotAssetCode = request.assetCode();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    snapshotService.snapshotInNewTransaction(pid, snapshotType, snapshotAssetCode);
                } catch (Exception e) {
                    log.warn("Post-transaction snapshot failed for portfolio {}: {}", pid, e.getMessage());
                }
            }
        });

        return mapper.toTransactionResponse(txn);
    }

    private void executeBuy(Portfolio portfolio, UserWallet wallet,
                            AssetType assetType, String assetCode,
                            BigDecimal quantity, BigDecimal totalCost, BigDecimal fee) {
        BigDecimal totalDebit = totalCost.add(fee);
        if (!wallet.hasSufficientBalance(totalDebit)) {
            String currency = appProperties.getPortfolio().getDefaultCurrency();
            throw new BusinessException("Alım gücü yetersiz. Gereken: " + totalDebit + " " + currency
                    + ", mevcut: " + wallet.getAvailableBalance());
        }

        wallet.debit(totalDebit);
        walletRepository.save(wallet);

        recordLedger(wallet, LedgerType.BUY, totalCost.negate(), "BUY " + quantity + " " + assetCode);
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            recordLedger(wallet, LedgerType.FEE, fee.negate(), "Fee for BUY " + assetCode);
        }

        PortfolioPosition position = positionRepository
                .findByPortfolioIdAndAssetTypeAndAssetCode(portfolio.getId(), assetType, assetCode)
                .orElseGet(() -> PortfolioPosition.builder()
                        .portfolio(portfolio)
                        .assetType(assetType)
                        .assetCode(assetCode)
                        .quantity(BigDecimal.ZERO)
                        .averageCostTry(BigDecimal.ZERO)
                        .totalCostTry(BigDecimal.ZERO)
                        .build());

        position.addQuantity(quantity, totalCost);
        positionRepository.save(position);
    }

    private BigDecimal executeSell(Portfolio portfolio, UserWallet wallet,
                                   AssetType assetType, String assetCode,
                                   BigDecimal quantity, BigDecimal totalCost, BigDecimal fee) {
        PortfolioPosition position = positionRepository
                .findByPortfolioIdAndAssetTypeAndAssetCode(portfolio.getId(), assetType, assetCode)
                .orElseThrow(() -> new ResourceNotFoundException("No position found for " + assetCode));

        if (!position.hasSufficientQuantity(quantity)) {
            throw new BusinessException("Yetersiz miktar. Sahip olunan: " + position.getQuantity() + ", satılmak istenen: " + quantity);
        }

        BigDecimal costBasis = position.getAverageCostTry().multiply(quantity).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal realizedPnl = totalCost.subtract(costBasis).subtract(fee);
        portfolio.addRealizedPnl(realizedPnl);
        portfolioRepository.save(portfolio);

        BigDecimal proceeds = totalCost.subtract(fee);
        wallet.credit(proceeds);
        walletRepository.save(wallet);

        recordLedger(wallet, LedgerType.SELL, proceeds, "SELL " + quantity + " " + assetCode);
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            recordLedger(wallet, LedgerType.FEE, fee.negate(), "Fee for SELL " + assetCode);
        }

        position.removeQuantity(quantity);
        positionRepository.save(position);

        return realizedPnl;
    }

    private void recordLedger(UserWallet wallet, LedgerType type, BigDecimal amount, String description) {
        WalletLedger ledger = WalletLedger.builder()
                .wallet(wallet)
                .ledgerType(type)
                .amount(amount)
                .balanceAfter(wallet.getBalance())
                .description(description)
                .build();
        ledgerRepository.save(ledger);
    }
}
