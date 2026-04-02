package com.finance.backend.service;

import com.finance.backend.dto.request.TransactionRequest;
import com.finance.backend.dto.response.TransactionResponse;
import com.finance.backend.exception.BadRequestException;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.mapper.PortfolioResponseMapper;
import com.finance.backend.model.*;
import com.finance.backend.repository.*;
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
    private static final int QTY_SCALE = 8;

    private final AssetPricingPort pricingPort;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioTransactionRepository transactionRepository;
    private final UserWalletRepository walletRepository;
    private final WalletLedgerRepository ledgerRepository;
    private final PortfolioResponseMapper mapper;
    private final PortfolioSnapshotService snapshotService;

    @Transactional
    public TransactionResponse execute(String userSub, Long portfolioId, TransactionRequest request) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));

        AssetType assetType = AssetType.valueOf(request.assetType());
        TransactionSide side = TransactionSide.valueOf(request.side());
        BigDecimal quantity = request.quantity().setScale(QTY_SCALE, RoundingMode.HALF_UP);
        BigDecimal fee = request.feeTry() != null ? request.feeTry().setScale(PRICE_SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Quantity must be positive");
        }

        BigDecimal unitPrice = pricingPort.getPriceTry(request.assetType(), request.assetCode());
        if (unitPrice == null) {
            throw new BadRequestException("Price not available for " + request.assetType() + ":" + request.assetCode());
        }
        unitPrice = unitPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalCost = unitPrice.multiply(quantity).setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        UserWallet wallet = walletRepository.findByPortfolioIdAndCurrency(portfolioId, "TRY")
                .orElseThrow(() -> new ResourceNotFoundException("TRY wallet not found"));

        if (side == TransactionSide.BUY) {
            executeBuy(portfolio, wallet, assetType, request.assetCode(), quantity, totalCost, fee);
        } else {
            executeSell(portfolio, wallet, assetType, request.assetCode(), quantity, totalCost, fee);
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
                .build();
        txn = transactionRepository.save(txn);

        log.info("{} {} {} @ {} TRY (fee={}) for portfolio {}",
                side, quantity, request.assetCode(), unitPrice, fee, portfolioId);

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
            throw new BusinessException("Insufficient balance. Required: " + totalDebit + " TRY, available: " + wallet.getAvailableBalance());
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

    private void executeSell(Portfolio portfolio, UserWallet wallet,
                             AssetType assetType, String assetCode,
                             BigDecimal quantity, BigDecimal totalCost, BigDecimal fee) {
        PortfolioPosition position = positionRepository
                .findByPortfolioIdAndAssetTypeAndAssetCode(portfolio.getId(), assetType, assetCode)
                .orElseThrow(() -> new ResourceNotFoundException("No position found for " + assetCode));

        if (!position.hasSufficientQuantity(quantity)) {
            throw new BusinessException("Insufficient quantity. Owned: " + position.getQuantity() + ", selling: " + quantity);
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
