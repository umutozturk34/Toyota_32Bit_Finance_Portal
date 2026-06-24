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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CRUD and lifecycle operations for price alerts. Thresholds are entered in any tradable currency
 * but stored in the asset's native quote currency (converted via live FOREX snapshots), and alert
 * targets are resolved to a tracked asset so only watched instruments can be alerted on.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class PriceAlertService {

    private final PriceAlertRepository repository;
    private final PriceAlertMapper mapper;
    private final AssetSnapshotCache assetSnapshotCache;
    private final TrackedAssetRepository trackedAssetRepository;
    private final PriceAlertProperties properties;

    /**
     * Creates an alert after enforcing the per-user and per-asset caps, resolving the asset against the
     * tracked universe, converting the threshold to the asset's native currency and rejecting duplicates.
     */
    @Transactional
    public PriceAlertResponse create(String userSub, PriceAlertCreateRequest request) {
        int maxPerUser = properties.maxPerUser();
        if (maxPerUser > 0 && repository.countByUserSub(userSub) >= maxPerUser) {
            throw new BadRequestException("error.priceAlert.maxReached", maxPerUser);
        }
        TrackedAsset trackedAsset = requireTrackedAsset(request.marketType(), request.assetCode());
        int maxPerAsset = properties.maxPerAsset();
        if (maxPerAsset > 0
                && repository.countByUserSubAndTrackedAsset_Id(userSub, trackedAsset.getId()) >= maxPerAsset) {
            throw new BadRequestException("error.priceAlert.maxPerAssetReached", maxPerAsset, trackedAsset.getAssetCode());
        }
        String nativeCurrency = resolveAssetCurrency(request.marketType(), trackedAsset.getAssetCode());
        BigDecimal thresholdNative = convertToCurrency(request.threshold(), request.currency(), nativeCurrency);
        if (repository.existsByUserSubAndTrackedAsset_IdAndDirectionAndThresholdAndActiveTrue(
                userSub, trackedAsset.getId(), request.direction(), thresholdNative)) {
            throw new BadRequestException("error.priceAlert.duplicate",
                    request.marketType(), trackedAsset.getAssetCode(),
                    request.direction(), thresholdNative);
        }
        PriceAlert entity = mapper.toEntity(request, userSub);
        entity.setThreshold(thresholdNative);
        entity.setCurrency(nativeCurrency);
        entity.setTrackedAsset(trackedAsset);
        entity.setAssetCode(trackedAsset.getAssetCode());
        PriceAlert saved = repository.save(entity);
        log.info("Price alert created userSub={} alertId={} market={} code={} dir={} threshold={} currency={}",
                userSub, saved.getId(), saved.getMarketType(), saved.getAssetCode(),
                saved.getDirection(), saved.getThreshold(), saved.getCurrency());
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

    /**
     * Lists the user's alerts enriched with live snapshot data (current price, change, image),
     * batching snapshot lookups per market type.
     */
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
            String nativeCurrency = alert.getCurrency() != null ? alert.getCurrency() : "TRY";
            alert.setThreshold(convertToCurrency(request.threshold(), nativeCurrency, nativeCurrency));
        }
        PriceAlert saved = repository.save(alert);
        log.info("Price alert updated alertId={} userSub={} dir={} threshold={}",
                id, userSub, saved.getDirection(), saved.getThreshold());
        return mapper.toResponse(saved);
    }

    /** Active, not-yet-triggered alerts for the given market, used by the evaluator each cycle. */
    @Transactional(readOnly = true)
    public List<PriceAlert> activeAlerts(MarketType marketType) {
        return repository.findByActiveTrueAndTrackedAsset_AssetType(
                TrackedAssetType.valueOf(marketType.name()));
    }

    /**
     * A keyset page (id &gt; {@code lastId}, ascending, at most {@code size}) of active, not-yet-triggered alerts
     * for the market. Keyset (not offset) paging lets the evaluator scan in bounded memory batches without
     * skipping rows as fired alerts deactivate mid-scan and would shift an offset window.
     */
    @Transactional(readOnly = true)
    public List<PriceAlert> activeAlertsAfter(MarketType marketType, long lastId, int size) {
        return repository.findByActiveTrueAndTrackedAsset_AssetTypeAndIdGreaterThan(
                TrackedAssetType.valueOf(marketType.name()), lastId,
                PageRequest.of(0, size, Sort.by("id").ascending()));
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

    private String resolveAssetCurrency(MarketType marketType, String assetCode) {
        AssetSnapshot snapshot = assetSnapshotCache.findByCode(marketType, assetCode).orElse(null);
        if (snapshot == null || snapshot.currency() == null || snapshot.currency().isBlank()) {
            return "TRY";
        }
        return snapshot.currency().toUpperCase();
    }

    /** Converts an amount between currencies by routing through TRY using live FOREX snapshots. */
    private BigDecimal convertToCurrency(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null) return null;
        String from = fromCurrency == null || fromCurrency.isBlank() ? "TRY" : fromCurrency.toUpperCase();
        String to = toCurrency == null || toCurrency.isBlank() ? "TRY" : toCurrency.toUpperCase();
        if (from.equals(to)) return amount;
        BigDecimal amountInTry = from.equals("TRY") ? amount : amount.multiply(fxRate(from));
        BigDecimal result = to.equals("TRY") ? amountInTry : amountInTry.divide(fxRate(to), 8, RoundingMode.HALF_UP);
        return result.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal fxRate(String currency) {
        AssetSnapshot fx = assetSnapshotCache
                .findByCodes(MarketType.FOREX, Set.of(currency))
                .get(currency);
        if (fx == null || fx.priceTry() == null || fx.priceTry().signum() <= 0) {
            throw new BadRequestException("error.priceAlert.unknownCurrency", currency);
        }
        return fx.priceTry();
    }
}
