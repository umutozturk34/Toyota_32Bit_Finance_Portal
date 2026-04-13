package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
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
    private final AppProperties appProperties;
    private final PortfolioRepository portfolioRepository;
    private final UserWalletRepository walletRepository;

    @Transactional
    public Portfolio ensurePortfolio(String userSub) {
        return portfolioRepository.findByUserSubAndName(userSub, appProperties.getPortfolio().getDefaultName())
                .orElseGet(() -> createDefaultPortfolio(userSub));
    }

    private Portfolio createDefaultPortfolio(String userSub) {
        Portfolio portfolio = Portfolio.builder()
                .userSub(userSub)
                .name(appProperties.getPortfolio().getDefaultName())
                .build();
        portfolio = portfolioRepository.save(portfolio);

        UserWallet wallet = UserWallet.builder()
                .portfolio(portfolio)
                .currency(appProperties.getPortfolio().getDefaultCurrency())
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .build();
        walletRepository.save(wallet);

        log.info("Created default portfolio and {} wallet for user {}",
                appProperties.getPortfolio().getDefaultCurrency(), userSub);
        return portfolio;
    }
}
