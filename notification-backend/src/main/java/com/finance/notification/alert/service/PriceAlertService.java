package com.finance.notification.alert.service;

import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.notification.alert.dto.PriceAlertCreateRequest;
import com.finance.notification.alert.dto.PriceAlertResponse;
import com.finance.notification.alert.dto.PriceAlertUpdateRequest;
import com.finance.notification.alert.mapper.PriceAlertMapper;
import com.finance.notification.alert.model.PriceAlert;
import com.finance.notification.alert.repository.PriceAlertRepository;
import com.finance.notification.config.PriceAlertProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class PriceAlertService {

    private final PriceAlertRepository repository;
    private final PriceAlertMapper mapper;
    private final AssetSnapshotCache assetSnapshotCache;
    private final TrackedAssetRepository trackedAssetRepository;
    private final PriceAlertProperties properties;

    @Transactional
    public PriceAlertResponse create(String userSub, PriceAlertCreateRequest request) {
        int maxPerUser = properties.maxPerUser();
        if (maxPerUser > 0 && repository.countByUserSub(userSub) >= maxPerUser) {
            throw new BadRequestException("error.priceAlert.maxReached", maxPerUser);
        }
        TrackedAsset trackedAsset = requireTrackedAsset(request.marketType(), request.assetCode());
        BigDecimal thresholdTry = toTry(request.threshold(), request.currency());
        if (repository.existsByUserSubAndTrackedAsset_IdAndDirectionAndThresholdAndActiveTrue(
                userSub, trackedAsset.getId(), request.direction(), thresholdTry)) {
            throw new BadRequestException("error.priceAlert.duplicate",
                    request.marketType(), trackedAsset.getAssetCode(),
                    request.direction(), thresholdTry);
        }
        PriceAlert entity = mapper.toEntity(request, userSub);
        entity.setThreshold(thresholdTry);
        entity.setTrackedAsset(trackedAsset);
        entity.setAssetCode(trackedAsset.getAssetCode());
        PriceAlert saved = repository.save(entity);
        log.info("Price alert created userSub={} alertId={} market={} code={} dir={} threshold={}",
                userSub, saved.getId(), saved.getMarketType(), saved.getAssetCode(),
                saved.getDirection(), saved.getThreshold());
        return mapper.toResponse(saved);
    }

    private TrackedAsset requireTrackedAsset(MarketType marketType, String rawCode) {
        TrackedAssetType trackedType = TrackedAssetType.valueOf(marketType.name());
        String normalizedCode = trackedType.normalizeCode(rawCode);
        return trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(trackedType, normalizedCode)
                .orElseThrow(() -> new BusinessException(
                        "error.priceAlert.assetNotTracked", marketType, normalizedCode));
    }

    @Transactional(readOnly = true)
    public Page<PriceAlertResponse> list(String userSub, int page, int size) {
        Page<PriceAlert> alerts = repository.findByUserSubOrderByCreatedAtDesc(userSub, PageRequest.of(page, size));
        if (alerts.isEmpty()) return alerts.map(mapper::toResponse);
        Map<MarketType, Map<String, AssetSnapshot>> snapshots = loadSnapshotsByMarket(alerts.getContent());
        return alerts.map(alert -> mapper.toResponse(alert, snapshots
                .getOrDefault(alert.getMarketType(), Map.of())
                .get(alert.getAssetCode())));
    }

    @Transactional
    public void delete(Long id, String userSub) {
        PriceAlert alert = ownedOr404(id, userSub);
        repository.delete(alert);
        log.info("Price alert deleted alertId={} userSub={}", id, userSub);
    }

    @Transactional
    public PriceAlertResponse reactivate(Long id, String userSub) {
        PriceAlert alert = ownedOr404(id, userSub);
        if (repository.existsByUserSubAndTrackedAsset_IdAndDirectionAndThresholdAndActiveTrue(
                userSub, alert.getTrackedAsset().getId(), alert.getDirection(), alert.getThreshold())) {
            throw new BadRequestException("error.priceAlert.duplicate",
                    alert.getMarketType(), alert.getAssetCode(),
                    alert.getDirection(), alert.getThreshold());
        }
        alert.reactivate();
        PriceAlert saved = repository.save(alert);
        log.info("Price alert reactivated alertId={} userSub={}", id, userSub);
        return mapper.toResponse(saved);
    }

    @Transactional
    public PriceAlertResponse update(Long id, String userSub, PriceAlertUpdateRequest request) {
        PriceAlert alert = ownedOr404(id, userSub);
        if (request.direction() != null) alert.setDirection(request.direction());
        if (request.threshold() != null) {
            alert.setThreshold(toTry(request.threshold(), alert.getCurrency()));
        }
        PriceAlert saved = repository.save(alert);
        log.info("Price alert updated alertId={} userSub={} dir={} threshold={}",
                id, userSub, saved.getDirection(), saved.getThreshold());
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PriceAlert> activeAlerts(MarketType marketType) {
        return repository.findByActiveTrueAndTrackedAsset_AssetType(
                TrackedAssetType.valueOf(marketType.name()));
    }

    @Transactional
    public void persist(PriceAlert alert) {
        repository.save(alert);
    }

    private Map<MarketType, Map<String, AssetSnapshot>> loadSnapshotsByMarket(List<PriceAlert> alerts) {
        Map<MarketType, Set<String>> codesByMarket = alerts.stream()
                .collect(Collectors.groupingBy(
                        PriceAlert::getMarketType,
                        Collectors.mapping(PriceAlert::getAssetCode, Collectors.toUnmodifiableSet())));
        Map<MarketType, Map<String, AssetSnapshot>> snapshots = new EnumMap<>(MarketType.class);
        codesByMarket.forEach((mt, codes) -> snapshots.put(mt, assetSnapshotCache.findByCodes(mt, codes)));
        return snapshots;
    }

    private PriceAlert ownedOr404(Long id, String userSub) {
        return repository.findById(id)
                .filter(a -> a.belongsTo(userSub))
                .orElseThrow(() -> new ResourceNotFoundException("error.priceAlert.notFound", id));
    }

    private BigDecimal toTry(BigDecimal amount, String currency) {
        if (amount == null) return null;
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency)) return amount;
        AssetSnapshot fx = assetSnapshotCache
                .findByCodes(MarketType.FOREX, Set.of(currency.toUpperCase()))
                .get(currency.toUpperCase());
        if (fx == null || fx.priceTry() == null || fx.priceTry().signum() <= 0) {
            throw new BadRequestException("error.priceAlert.unknownCurrency", currency);
        }
        return amount.multiply(fx.priceTry()).setScale(4, RoundingMode.HALF_UP);
    }
}
