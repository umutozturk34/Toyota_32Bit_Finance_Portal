package com.finance.backend.service;

import com.finance.backend.dto.request.PortfolioCreateRequest;
import com.finance.backend.dto.response.PortfolioResponse;
import com.finance.backend.dto.response.TransactionResponse;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.mapper.PortfolioResponseMapper;
import com.finance.backend.model.Portfolio;
import com.finance.backend.model.UserWallet;
import com.finance.backend.repository.PortfolioRepository;
import com.finance.backend.repository.PortfolioTransactionRepository;
import com.finance.backend.repository.UserWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortfolioCrudService {

    private final PortfolioRepository portfolioRepository;
    private final UserWalletRepository walletRepository;
    private final PortfolioTransactionRepository transactionRepository;
    private final PortfolioResponseMapper mapper;

    @Transactional(readOnly = true)
    public List<PortfolioResponse> listPortfolios(String userSub) {
        return portfolioRepository.findByUserSub(userSub).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PortfolioResponse createPortfolio(String userSub, PortfolioCreateRequest request) {
        portfolioRepository.findByUserSubAndName(userSub, request.name())
                .ifPresent(p -> { throw new BusinessException("Portfolio with name '" + request.name() + "' already exists"); });

        Portfolio portfolio = Portfolio.builder().userSub(userSub).name(request.name()).build();
        portfolio = portfolioRepository.save(portfolio);

        UserWallet wallet = UserWallet.builder()
                .portfolio(portfolio)
                .currency("TRY")
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .build();
        walletRepository.save(wallet);

        return toResponse(portfolio);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listTransactions(Long portfolioId) {
        return mapper.toTransactionResponses(
                transactionRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioId));
    }

    private PortfolioResponse toResponse(Portfolio portfolio) {
        BigDecimal cash = walletRepository.findByPortfolioIdAndCurrency(portfolio.getId(), "TRY")
                .map(UserWallet::getBalance)
                .orElse(BigDecimal.ZERO);
        return mapper.toPortfolioResponse(portfolio, cash);
    }
}
