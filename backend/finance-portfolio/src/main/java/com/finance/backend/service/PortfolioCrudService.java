package com.finance.backend.service;

import com.finance.backend.config.PortfolioProperties;
import com.finance.backend.dto.request.PortfolioCreateRequest;
import com.finance.backend.dto.response.PagedResponse;
import com.finance.backend.dto.response.PortfolioResponse;
import com.finance.backend.dto.response.TransactionResponse;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.model.AssetType;
import com.finance.backend.mapper.PortfolioResponseMapper;
import com.finance.backend.util.EnumParser;
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

    private final PortfolioProperties portfolioProperties;
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
                .currency(portfolioProperties.getDefaultCurrency())
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

        AssetType filterType = assetType == null || assetType.isBlank()
                ? null
                : EnumParser.parseOrBadRequest(AssetType.class, assetType.toUpperCase(), "asset type");
        if (filterType != null) {
            AssetType fixed = filterType;
            spec = spec.and((root, query, cb) -> cb.equal(root.get("assetType"), fixed));
        }

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("assetCode")), pattern));
        }

        PageRequest pageRequest = PageRequest.of(page, size, buildTransactionSort(sortBy, direction));
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
                        portfolioProperties.getDefaultCurrency())
                .map(UserWallet::getBalance)
                .orElse(BigDecimal.ZERO);
        return mapper.toPortfolioResponse(portfolio, cash);
    }
}
