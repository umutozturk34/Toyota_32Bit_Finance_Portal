package com.finance.backend.service;

import com.finance.backend.config.PortfolioProperties;
import com.finance.backend.model.Portfolio;
import com.finance.backend.model.UserWallet;
import com.finance.backend.model.value.MoneyTRY;
import com.finance.backend.repository.PortfolioRepository;
import com.finance.backend.repository.UserWalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioBootstrapService {
    private final PortfolioProperties portfolioProperties;
    private final PortfolioRepository portfolioRepository;
    private final UserWalletRepository walletRepository;

    @Transactional
    public Portfolio ensurePortfolio(String userSub) {
        return portfolioRepository.findByUserSubAndName(userSub, portfolioProperties.getDefaultName())
                .orElseGet(() -> createDefaultPortfolio(userSub));
    }

    private Portfolio createDefaultPortfolio(String userSub) {
        Portfolio portfolio = Portfolio.builder()
                .userSub(userSub)
                .name(portfolioProperties.getDefaultName())
                .build();
        portfolio = portfolioRepository.save(portfolio);

        UserWallet wallet = UserWallet.builder()
                .portfolio(portfolio)
                .currency(portfolioProperties.getDefaultCurrency())
                .balance(MoneyTRY.ZERO)
                .availableBalance(MoneyTRY.ZERO)
                .build();
        walletRepository.save(wallet);

        log.info("Created default portfolio and {} wallet for user {}",
                portfolioProperties.getDefaultCurrency(), userSub);
        return portfolio;
    }
}
