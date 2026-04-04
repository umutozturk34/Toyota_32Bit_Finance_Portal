package com.finance.backend.service;

import com.finance.backend.exception.BusinessException;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.model.*;
import com.finance.backend.repository.UserWalletRepository;
import com.finance.backend.repository.WalletLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Log4j2
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000000.0000");

    private final PortfolioBootstrapService bootstrapService;
    private final UserWalletRepository walletRepository;
    private final WalletLedgerRepository ledgerRepository;

    @Transactional
    public void initialize(String userSub) {
        Portfolio portfolio = bootstrapService.ensurePortfolio(userSub);

        UserWallet wallet = walletRepository.findByPortfolioIdAndCurrency(portfolio.getId(), "TRY")
                .orElseThrow(() -> new ResourceNotFoundException("TRY wallet not found for portfolio " + portfolio.getId()));

        if (ledgerRepository.existsByWalletIdAndLedgerType(wallet.getId(), LedgerType.INITIAL_DEPOSIT)) {
            throw new BusinessException("Initial deposit already applied for this portfolio");
        }

        wallet.setBalance(INITIAL_BALANCE);
        wallet.setAvailableBalance(INITIAL_BALANCE);
        walletRepository.save(wallet);

        WalletLedger ledger = WalletLedger.builder()
                .wallet(wallet)
                .ledgerType(LedgerType.INITIAL_DEPOSIT)
                .amount(INITIAL_BALANCE)
                .balanceAfter(INITIAL_BALANCE)
                .description("Demo initial deposit")
                .build();
        ledgerRepository.save(ledger);

        log.info("Onboarding completed for user {}: {} TRY deposited", userSub, INITIAL_BALANCE);
    }
}
