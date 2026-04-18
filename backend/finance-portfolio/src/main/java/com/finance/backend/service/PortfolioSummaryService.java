package com.finance.backend.service;

import com.finance.backend.dto.response.AllocationItem;
import com.finance.backend.dto.response.PagedResponse;
import com.finance.backend.dto.response.PortfolioSummaryResponse;
import com.finance.backend.dto.response.PositionResponse;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.mapper.PortfolioResponseMapper;
import com.finance.backend.model.AssetType;
import com.finance.backend.model.Portfolio;
import com.finance.backend.model.PortfolioPosition;
import com.finance.backend.model.UserWallet;
import com.finance.backend.repository.PortfolioPositionRepository;
import com.finance.backend.repository.PortfolioRepository;
import com.finance.backend.repository.PortfolioTransactionRepository;
import com.finance.backend.repository.UserWalletRepository;
import com.finance.backend.service.AssetPricingPort.AssetKey;
import com.finance.backend.service.AssetPricingPort.AssetMeta;
import com.finance.backend.service.AssetPricingPort.PriceBundle;
import com.finance.backend.util.EnumParser;
import com.finance.backend.config.AppProperties;
import com.finance.backend.model.TransactionSide;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.Comparator;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioSummaryService {

    private static final int SCALE = 4;

    private final AssetPricingPort pricingPort;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioTransactionRepository transactionRepository;
    private final UserWalletRepository walletRepository;
    private final PortfolioResponseMapper responseMapper;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(Long portfolioId) {
        List<PortfolioPosition> positions = positionRepository
                .findByPortfolioIdAndQuantityGreaterThan(portfolioId, BigDecimal.ZERO);

        Map<AssetKey, PriceBundle> bundles = pricingPort.getBundles(toKeys(positions));
        return positions.stream()
                .map(pos -> toPositionResponse(pos, bundles.get(toKey(pos))))
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<PositionResponse> getPositionsPaged(Long portfolioId, String search,
                                                               String assetType, String sortBy, String direction,
                                                               int page, int size) {
        List<PositionResponse> all = getPositions(portfolioId);

        if (assetType != null && !assetType.isBlank()) {
            all = all.stream().filter(r -> assetType.equalsIgnoreCase(r.assetType())).toList();
        }

        if (search != null && !search.isBlank()) {
            String lower = search.toLowerCase();
            all = all.stream()
                    .filter(r -> r.assetCode().toLowerCase().contains(lower)
                            || (r.assetName() != null && r.assetName().toLowerCase().contains(lower)))
                    .toList();
        }

        if (sortBy != null && !sortBy.isBlank()) {
            Comparator<PositionResponse> comparator = buildPositionComparator(sortBy);
            if ("asc".equalsIgnoreCase(direction)) {
                all = all.stream().sorted(comparator).toList();
            } else {
                all = all.stream().sorted(comparator.reversed()).toList();
            }
        }

        long total = all.size();
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());

        return PagedResponse.of(all.subList(from, to), page, size, total);
    }

    private Comparator<PositionResponse> buildPositionComparator(String sortBy) {
        return switch (sortBy != null ? sortBy : "currentValue") {
            case "profitPercent" -> Comparator.comparing(PositionResponse::pnlPercent, Comparator.nullsLast(Comparator.naturalOrder()));
            case "profitAmount" -> Comparator.comparing(PositionResponse::pnlTry, Comparator.nullsLast(Comparator.naturalOrder()));
            case "assetCode" -> Comparator.comparing(PositionResponse::assetCode);
            case "quantity" -> Comparator.comparing(PositionResponse::quantity);
            default -> Comparator.comparing(PositionResponse::marketValueTry, Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getSummary(Long portfolioId, String assetType) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
        List<PortfolioPosition> positions = positionRepository
                .findByPortfolioIdAndQuantityGreaterThan(portfolioId, BigDecimal.ZERO);

        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetType, "asset type");

        if (filterType != null) {
            positions = positions.stream()
                    .filter(p -> p.getAssetType() == filterType)
                    .toList();
        }

        UserWallet wallet = findWallet(portfolioId);

        Map<AssetKey, BigDecimal> prices = pricingPort.getPricesTry(toKeys(positions));

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (PortfolioPosition pos : positions) {
            BigDecimal price = prices.get(toKey(pos));
            if (price != null) {
                totalValue = totalValue.add(price.multiply(pos.getQuantity()));
            }
            totalCost = totalCost.add(pos.getTotalCostTry());
        }

        totalValue = totalValue.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal cashBalance = filterType == null ? wallet.getBalance() : BigDecimal.ZERO;
        BigDecimal grandTotal = totalValue.add(cashBalance);
        BigDecimal unrealizedPnl = totalValue.subtract(totalCost).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal realizedPnl = filterType == null
                ? portfolio.getRealizedPnlTry()
                : calculateRealizedPnlByType(portfolioId, filterType);
        BigDecimal totalPnl = unrealizedPnl.add(realizedPnl).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal denominator = totalCost.compareTo(BigDecimal.ZERO) > 0
                ? totalCost
                : appProperties.getPortfolio().getInitialBalance();
        BigDecimal pnlPercent = totalPnl.multiply(new BigDecimal("100"))
                .divide(denominator, SCALE, RoundingMode.HALF_UP);

        return new PortfolioSummaryResponse(grandTotal, totalCost, cashBalance, unrealizedPnl, realizedPnl, totalPnl, pnlPercent);
    }

    @Transactional(readOnly = true)
    public List<AllocationItem> getAllocation(Long portfolioId, String mode, String assetTypeFilter) {
        List<PortfolioPosition> positions = positionRepository
                .findByPortfolioIdAndQuantityGreaterThan(portfolioId, BigDecimal.ZERO);

        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetTypeFilter, "asset type");
        if (filterType != null) {
            AssetType fixed = filterType;
            positions = positions.stream().filter(p -> p.getAssetType() == fixed).toList();
        }

        UserWallet wallet = findWallet(portfolioId);

        boolean byType = "assetType".equals(mode);
        Map<String, BigDecimal> buckets = new LinkedHashMap<>();
        Map<String, String> bucketTypes = new LinkedHashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        Map<AssetKey, BigDecimal> prices = pricingPort.getPricesTry(toKeys(positions));

        for (PortfolioPosition pos : positions) {
            BigDecimal price = prices.get(toKey(pos));
            BigDecimal marketValue = price != null
                    ? price.multiply(pos.getQuantity()).setScale(SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            String key = byType ? pos.getAssetType().name() : pos.getAssetCode();
            buckets.merge(key, marketValue, BigDecimal::add);
            bucketTypes.putIfAbsent(key, pos.getAssetType().name());
            totalValue = totalValue.add(marketValue);
        }

        BigDecimal finalTotal = totalValue.add(wallet.getBalance());
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

    private PositionResponse toPositionResponse(PortfolioPosition pos, PriceBundle bundle) {
        PriceBundle effective = bundle != null ? bundle : new PriceBundle(null, null, new AssetMeta(null, null));
        BigDecimal currentPrice = effective.price() != null ? effective.price() : BigDecimal.ZERO;
        BigDecimal marketValue = currentPrice.multiply(pos.getQuantity()).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal pnl = marketValue.subtract(pos.getTotalCostTry());
        BigDecimal pnlPercent = pos.getTotalCostTry().compareTo(BigDecimal.ZERO) > 0
                ? pnl.multiply(new BigDecimal("100")).divide(pos.getTotalCostTry(), SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal sellPriceTry = effective.sellPrice() != null ? effective.sellPrice() : currentPrice;
        BigDecimal commissionRate = getCommissionRate(pos.getAssetType());
        AssetMeta meta = effective.meta() != null ? effective.meta() : new AssetMeta(null, null);

        return responseMapper.toPositionResponse(pos, currentPrice, sellPriceTry, commissionRate, marketValue, pnl, pnlPercent, meta.name(), meta.image());
    }

    private List<AssetKey> toKeys(List<PortfolioPosition> positions) {
        return positions.stream()
                .map(this::toKey)
                .toList();
    }

    private AssetKey toKey(PortfolioPosition pos) {
        return new AssetKey(pos.getAssetType().name(), pos.getAssetCode());
    }

    private BigDecimal getCommissionRate(AssetType assetType) {
        AppProperties.Commission commission = appProperties.getCommission();
        return switch (assetType) {
            case STOCK -> commission.getStockRate();
            case CRYPTO -> commission.getCryptoRate();
            case FUND -> commission.getFundRate();
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal calculateRealizedPnlByType(Long portfolioId, AssetType assetType) {
        return transactionRepository
                .findByPortfolioIdAndAssetTypeAndSide(portfolioId, assetType, TransactionSide.SELL)
                .stream()
                .map(txn -> txn.getRealizedPnlTry() != null ? txn.getRealizedPnlTry() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private UserWallet findWallet(Long portfolioId) {
        String currency = appProperties.getPortfolio().getDefaultCurrency();
        return walletRepository.findByPortfolioIdAndCurrency(portfolioId, currency)
                .orElseThrow(() -> new ResourceNotFoundException(currency + " wallet not found for portfolio " + portfolioId));
    }
}
