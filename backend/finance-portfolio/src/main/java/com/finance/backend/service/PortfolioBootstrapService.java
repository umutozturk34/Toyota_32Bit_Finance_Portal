package com.finance.backend.service;

import com.finance.backend.model.Portfolio;
import com.finance.backend.model.UserWallet;
import com.finance.backend.repository.PortfolioRepository;
import com.finance.backend.repository.UserWalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioBootstrapService {

    private static final String DEFAULT_PORTFOLIO_NAME = "Ana Portföy";
    private static final String DEFAULT_CURRENCY = "TRY";

    private final PortfolioRepository portfolioRepository;
    private final UserWalletRepository walletRepository;

    @Transactional
    public Portfolio ensurePortfolio(String userSub) {
        return portfolioRepository.findByUserSubAndName(userSub, DEFAULT_PORTFOLIO_NAME)
                .orElseGet(() -> createDefaultPortfolio(userSub));
    }

    private Portfolio createDefaultPortfolio(String userSub) {
        Portfolio portfolio = Portfolio.builder()
                .userSub(userSub)
                .name(DEFAULT_PORTFOLIO_NAME)
                .build();
        portfolio = portfolioRepository.save(portfolio);

        UserWallet wallet = UserWallet.builder()
                .portfolio(portfolio)
                .currency(DEFAULT_CURRENCY)
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .build();
        walletRepository.save(wallet);

        log.info("Created default portfolio and TRY wallet for user {}", userSub);
        return portfolio;
    }
}
