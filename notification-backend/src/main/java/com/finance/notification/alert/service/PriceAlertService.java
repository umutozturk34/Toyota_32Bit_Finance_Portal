package com.finance.notification.alert.service;

import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.notification.alert.dto.PriceAlertCreateRequest;
import com.finance.notification.alert.dto.PriceAlertResponse;
import com.finance.notification.alert.mapper.PriceAlertMapper;
import com.finance.notification.alert.model.PriceAlert;
import com.finance.notification.alert.repository.PriceAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceAlertService {

    private final PriceAlertRepository repository;
    private final PriceAlertMapper mapper;
    private final AssetSnapshotCache assetSnapshotCache;

    @Transactional
    public PriceAlertResponse create(String userSub, PriceAlertCreateRequest request) {
        PriceAlert entity = mapper.toEntity(request, userSub);
        return mapper.toResponse(repository.save(entity));
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
    }

    @Transactional
    public PriceAlertResponse reactivate(Long id, String userSub) {
        PriceAlert alert = ownedOr404(id, userSub);
        alert.reactivate();
        return mapper.toResponse(repository.save(alert));
    }

    @Transactional(readOnly = true)
    public List<PriceAlert> activeAlerts(MarketType marketType) {
        return repository.findByActiveTrueAndMarketType(marketType);
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
                .orElseThrow(() -> new ResourceNotFoundException("Price alert not found id=" + id));
    }
}
