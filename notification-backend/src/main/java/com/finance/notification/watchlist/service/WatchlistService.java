package com.finance.notification.watchlist.service;

import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.notification.watchlist.dto.WatchlistItemCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistItemResponse;
import com.finance.notification.watchlist.dto.WatchlistItemUpdateRequest;
import com.finance.notification.watchlist.mapper.WatchlistItemMapper;
import com.finance.notification.watchlist.model.Watchlist;
import com.finance.notification.watchlist.model.WatchlistItem;
import com.finance.notification.watchlist.model.WatchlistSortBy;
import com.finance.notification.watchlist.repository.WatchlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private static final Sort DEFAULT_DB_SORT = Sort.by(Sort.Direction.ASC, "displayOrder");

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
                .orElseGet(() -> createItem(parent, userSub, request));
    }

    @Transactional
    public WatchlistItemResponse addToDefault(String userSub, WatchlistItemCreateRequest request) {
        Watchlist defaultList = managementService.ensureDefault(userSub);
        return addToList(defaultList.getId(), userSub, request);
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> listItems(Long watchlistId, String userSub,
                                                  WatchlistSortBy sortBy, Sort.Direction direction) {
        managementService.requireOwned(watchlistId, userSub);
        Sort dbSort = sortBy.isDbSortable()
                ? Sort.by(sortBy.toDbOrder(direction))
                : DEFAULT_DB_SORT;
        List<WatchlistItem> items = repository.findByWatchlistId(watchlistId, dbSort);
        List<WatchlistItemResponse> enriched = enrich(items);
        if (!sortBy.isDbSortable()) {
            return enriched.stream()
                    .sorted(sortBy.postEnrichComparator(direction))
                    .toList();
        }
        return enriched;
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> listAllItems(String userSub) {
        return enrich(repository.findByUserSubOrderByCreatedAtDesc(userSub));
    }

    @Transactional
    public List<WatchlistItemResponse> reorder(Long watchlistId, String userSub, List<Long> itemIds) {
        managementService.requireOwned(watchlistId, userSub);
        List<WatchlistItem> existing = repository.findByWatchlistId(watchlistId, DEFAULT_DB_SORT);
        validateReorder(existing, itemIds, watchlistId);
        Map<Long, WatchlistItem> itemsById = existing.stream()
                .collect(Collectors.toUnmodifiableMap(WatchlistItem::getId, item -> item));
        for (int i = 0; i < itemIds.size(); i++) {
            itemsById.get(itemIds.get(i)).setDisplayOrder(i + 1);
        }
        repository.saveAll(existing);
        log.info("Watchlist reordered watchlistId={} userSub={} count={}",
                watchlistId, userSub, itemIds.size());
        return enrich(existing.stream()
                .sorted(Comparator.comparing(WatchlistItem::getDisplayOrder))
                .toList());
    }

    @Transactional
    public void removeItem(Long itemId, String userSub) {
        WatchlistItem item = ownedItemOr404(itemId, userSub);
        repository.delete(item);
        log.info("Watchlist item removed itemId={} userSub={} market={} code={}",
                itemId, userSub, item.getMarketType(), item.getAssetCode());
    }

    @Transactional
    public WatchlistItemResponse updateItem(Long itemId, String userSub, WatchlistItemUpdateRequest request) {
        WatchlistItem item = ownedItemOr404(itemId, userSub);
        if (request.note() != null) item.setNote(request.note().isBlank() ? null : request.note());
        if (request.deltaThreshold() != null) item.setDeltaThreshold(request.deltaThreshold());
        WatchlistItem saved = repository.save(item);
        log.info("Watchlist item updated itemId={} userSub={} deltaThreshold={}",
                itemId, userSub, saved.getDeltaThreshold());
        return enrich(List.of(saved)).get(0);
    }

    @Transactional(readOnly = true)
    public List<WatchlistItem> itemsForMarket(MarketType marketType) {
        return repository.findByMarketType(marketType);
    }

    @Transactional
    public void persist(WatchlistItem item) {
        repository.save(item);
    }

    private WatchlistItemResponse createItem(Watchlist parent, String userSub, WatchlistItemCreateRequest request) {
        WatchlistItem entity = mapper.toEntity(request, userSub);
        entity.setWatchlistId(parent.getId());
        entity.setDisplayOrder(repository.findMaxDisplayOrderByWatchlistId(parent.getId()) + 1);
        assetSnapshotCache.findByCode(entity.getMarketType(), entity.getAssetCode())
                .map(AssetSnapshot::priceTry)
                .ifPresent(entity::recordObservation);
        WatchlistItem saved = repository.save(entity);
        log.info("Watchlist item added userSub={} watchlistId={} market={} code={} itemId={} baseline={}",
                userSub, parent.getId(), saved.getMarketType(), saved.getAssetCode(),
                saved.getId(), saved.getLastSeenPrice());
        return mapper.toResponse(saved);
    }

    private void validateReorder(List<WatchlistItem> existing, List<Long> itemIds, Long watchlistId) {
        if (itemIds.size() != existing.size()) {
            throw new BadRequestException(
                    "Reorder requires every item id; expected " + existing.size() + " got " + itemIds.size());
        }
        Set<Long> existingIds = existing.stream()
                .map(WatchlistItem::getId)
                .collect(Collectors.toCollection(HashSet::new));
        if (!new HashSet<>(itemIds).equals(existingIds)) {
            throw new BadRequestException("Reorder ids must match watchlist " + watchlistId + " exactly");
        }
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
