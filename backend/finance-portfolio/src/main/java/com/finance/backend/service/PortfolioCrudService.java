package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.request.PortfolioCreateRequest;
import com.finance.backend.dto.response.PagedResponse;
import com.finance.backend.dto.response.PortfolioResponse;
import com.finance.backend.dto.response.TransactionResponse;
import com.finance.backend.exception.BadRequestException;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.model.AssetType;
import com.finance.backend.mapper.PortfolioResponseMapper;
import com.finance.backend.model.Portfolio;
import com.finance.backend.model.PortfolioTransaction;
import com.finance.backend.model.UserWallet;
import com.finance.backend.repository.PortfolioRepository;
import com.finance.backend.repository.PortfolioTransactionRepository;
import com.finance.backend.repository.UserWalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioCrudService {

    private final AppProperties appProperties;
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
                .currency(appProperties.getPortfolio().getDefaultCurrency())
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

    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> listTransactionsPaged(Long portfolioId, String search,
                                                                      String assetType, String sortBy, String direction,
                                                                      int page, int size) {
        Specification<PortfolioTransaction> spec = (root, query, cb) ->
                cb.equal(root.get("portfolioId"), portfolioId);

        if (assetType != null && !assetType.isBlank()) {
            try {
                AssetType type = AssetType.valueOf(assetType.toUpperCase());
                spec = spec.and((root, query, cb) -> cb.equal(root.get("assetType"), type));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid asset type: " + assetType);
            }
        }

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("assetCode")), pattern));
        }

        PageRequest pageRequest = (sortBy != null && !sortBy.isBlank())
                ? PageRequest.of(page, size, buildTransactionSort(sortBy, direction))
                : PageRequest.of(page, size);
        Page<PortfolioTransaction> result = transactionRepository.findAll(spec, pageRequest);

        return PagedResponse.of(
                mapper.toTransactionResponses(result.getContent()),
                page, size, result.getTotalElements());
    }

    private Sort buildTransactionSort(String sortBy, String direction) {
        String field = switch (sortBy != null ? sortBy : "createdAt") {
            case "totalCostTry" -> "totalCostTry";
            case "assetCode" -> "assetCode";
            default -> "createdAt";
        };
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, field);
    }

    private PortfolioResponse toResponse(Portfolio portfolio) {
        BigDecimal cash = walletRepository.findByPortfolioIdAndCurrency(
                        portfolio.getId(),
                        appProperties.getPortfolio().getDefaultCurrency())
                .map(UserWallet::getBalance)
                .orElse(BigDecimal.ZERO);
        return mapper.toPortfolioResponse(portfolio, cash);
    }
}
