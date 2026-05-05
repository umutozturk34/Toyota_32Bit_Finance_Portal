package com.finance.notification.watchlist.service;

import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.notification.watchlist.dto.WatchlistItemCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistItemResponse;
import com.finance.notification.watchlist.mapper.WatchlistItemMapper;
import com.finance.notification.watchlist.model.Watchlist;
import com.finance.notification.watchlist.model.WatchlistItem;
import com.finance.notification.watchlist.repository.WatchlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistItemRepository repository;
    private final WatchlistItemMapper mapper;
    private final WatchlistManagementService managementService;
    private final AssetSnapshotCache assetSnapshotCache;

    @Transactional
    public WatchlistItemResponse addToList(Long watchlistId, String userSub,
                                           WatchlistItemCreateRequest request) {
        Watchlist parent = managementService.requireOwned(watchlistId, userSub);
        return repository.findByWatchlistIdAndMarketTypeAndAssetCode(
                        parent.getId(), request.marketType(), request.assetCode())
                .map(mapper::toResponse)
                .orElseGet(() -> {
                    WatchlistItem entity = mapper.toEntity(request, userSub);
                    entity.setWatchlistId(parent.getId());
                    return mapper.toResponse(repository.save(entity));
                });
    }

    @Transactional
    public WatchlistItemResponse addToDefault(String userSub, WatchlistItemCreateRequest request) {
        Watchlist defaultList = managementService.ensureDefault(userSub);
        return addToList(defaultList.getId(), userSub, request);
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> listItems(Long watchlistId, String userSub) {
        managementService.requireOwned(watchlistId, userSub);
        return enrich(repository.findByWatchlistIdOrderByCreatedAtDesc(watchlistId));
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> listAllItems(String userSub) {
        return enrich(repository.findByUserSubOrderByCreatedAtDesc(userSub));
    }

    @Transactional
    public void removeItem(Long itemId, String userSub) {
        WatchlistItem item = ownedItemOr404(itemId, userSub);
        repository.delete(item);
    }

    @Transactional(readOnly = true)
    public List<WatchlistItem> itemsForMarket(MarketType marketType) {
        return repository.findByMarketType(marketType);
    }

    @Transactional
    public void persist(WatchlistItem item) {
        repository.save(item);
    }

    private List<WatchlistItemResponse> enrich(List<WatchlistItem> items) {
        if (items.isEmpty()) return List.of();
        Map<MarketType, Map<String, AssetSnapshot>> snapshots = loadSnapshotsByMarket(items);
        return items.stream()
                .map(item -> mapper.toResponse(item, snapshots
                        .getOrDefault(item.getMarketType(), Map.of())
                        .get(item.getAssetCode())))
                .toList();
    }

    private Map<MarketType, Map<String, AssetSnapshot>> loadSnapshotsByMarket(List<WatchlistItem> items) {
        Map<MarketType, Set<String>> codesByMarket = items.stream()
                .collect(Collectors.groupingBy(
                        WatchlistItem::getMarketType,
                        Collectors.mapping(WatchlistItem::getAssetCode, Collectors.toUnmodifiableSet())));
        Map<MarketType, Map<String, AssetSnapshot>> snapshots = new EnumMap<>(MarketType.class);
        codesByMarket.forEach((mt, codes) -> snapshots.put(mt, assetSnapshotCache.findByCodes(mt, codes)));
        return snapshots;
    }

    private WatchlistItem ownedItemOr404(Long itemId, String userSub) {
        return repository.findById(itemId)
                .filter(i -> i.belongsTo(userSub))
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist item not found id=" + itemId));
    }
}
