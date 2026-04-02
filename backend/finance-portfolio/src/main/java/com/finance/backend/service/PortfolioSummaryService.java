package com.finance.backend.service;

import com.finance.backend.dto.response.AllocationItem;
import com.finance.backend.dto.response.PortfolioSummaryResponse;
import com.finance.backend.dto.response.PositionResponse;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.mapper.PortfolioResponseMapper;
import com.finance.backend.model.Portfolio;
import com.finance.backend.model.PortfolioPosition;
import com.finance.backend.model.UserWallet;
import com.finance.backend.repository.PortfolioPositionRepository;
import com.finance.backend.repository.PortfolioRepository;
import com.finance.backend.repository.UserWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PortfolioSummaryService {

    private static final int SCALE = 4;

    private final AssetPricingPort pricingPort;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final UserWalletRepository walletRepository;
    private final PortfolioResponseMapper responseMapper;

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(Long portfolioId) {
        List<PortfolioPosition> positions = positionRepository
                .findByPortfolioIdAndQuantityGreaterThan(portfolioId, BigDecimal.ZERO);

        return positions.stream().map(this::toPositionResponse).toList();
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getSummary(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
        List<PortfolioPosition> positions = positionRepository
                .findByPortfolioIdAndQuantityGreaterThan(portfolioId, BigDecimal.ZERO);
        UserWallet wallet = findWallet(portfolioId);

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (PortfolioPosition pos : positions) {
            BigDecimal price = pricingPort.getPriceTry(pos.getAssetType().name(), pos.getAssetCode());
            if (price != null) {
                totalValue = totalValue.add(price.multiply(pos.getQuantity()));
            }
            totalCost = totalCost.add(pos.getTotalCostTry());
        }

        totalValue = totalValue.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal cashBalance = wallet.getBalance();
        BigDecimal grandTotal = totalValue.add(cashBalance);
        BigDecimal unrealizedPnl = totalValue.subtract(totalCost).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal realizedPnl = portfolio.getRealizedPnlTry();
        BigDecimal totalPnl = unrealizedPnl.add(realizedPnl).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal initialDeposit = new BigDecimal("1000000");
        BigDecimal pnlPercent = totalPnl.multiply(new BigDecimal("100"))
                .divide(initialDeposit, SCALE, RoundingMode.HALF_UP);

        return new PortfolioSummaryResponse(grandTotal, totalCost, cashBalance, unrealizedPnl, realizedPnl, totalPnl, pnlPercent);
    }

    @Transactional(readOnly = true)
    public List<AllocationItem> getAllocation(Long portfolioId, String mode) {
        List<PortfolioPosition> positions = positionRepository
                .findByPortfolioIdAndQuantityGreaterThan(portfolioId, BigDecimal.ZERO);
        UserWallet wallet = findWallet(portfolioId);

        boolean byType = "assetType".equals(mode);
        Map<String, BigDecimal> buckets = new LinkedHashMap<>();
        Map<String, String> bucketTypes = new LinkedHashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        for (PortfolioPosition pos : positions) {
            BigDecimal price = pricingPort.getPriceTry(pos.getAssetType().name(), pos.getAssetCode());
            BigDecimal marketValue = price != null
                    ? price.multiply(pos.getQuantity()).setScale(SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            String key = byType ? pos.getAssetType().name() : pos.getAssetCode();
            buckets.merge(key, marketValue, BigDecimal::add);
            bucketTypes.putIfAbsent(key, pos.getAssetType().name());
            totalValue = totalValue.add(marketValue);
        }

        BigDecimal cashBalance = wallet.getBalance();
        buckets.put("CASH", cashBalance);
        bucketTypes.put("CASH", "CASH");
        totalValue = totalValue.add(cashBalance);

        BigDecimal finalTotal = totalValue;
        return buckets.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> new AllocationItem(
                        e.getKey(),
                        bucketTypes.get(e.getKey()),
                        e.getValue().setScale(SCALE, RoundingMode.HALF_UP),
                        finalTotal.compareTo(BigDecimal.ZERO) > 0
                                ? e.getValue().multiply(new BigDecimal("100")).divide(finalTotal, SCALE, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO
                ))
                .toList();
    }

    private PositionResponse toPositionResponse(PortfolioPosition pos) {
        BigDecimal price = pricingPort.getPriceTry(pos.getAssetType().name(), pos.getAssetCode());
        BigDecimal currentPrice = price != null ? price.setScale(SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal marketValue = currentPrice.multiply(pos.getQuantity()).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal pnl = marketValue.subtract(pos.getTotalCostTry());
        BigDecimal pnlPercent = pos.getTotalCostTry().compareTo(BigDecimal.ZERO) > 0
                ? pnl.multiply(new BigDecimal("100")).divide(pos.getTotalCostTry(), SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return responseMapper.toPositionResponse(pos, currentPrice, marketValue, pnl, pnlPercent);
    }

    private UserWallet findWallet(Long portfolioId) {
        return walletRepository.findByPortfolioIdAndCurrency(portfolioId, "TRY")
                .orElseThrow(() -> new ResourceNotFoundException("TRY wallet not found for portfolio " + portfolioId));
    }
}
