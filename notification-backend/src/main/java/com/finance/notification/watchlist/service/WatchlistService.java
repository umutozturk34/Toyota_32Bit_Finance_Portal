package com.finance.notification.watchlist.service;

import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.notification.watchlist.dto.WatchlistItemCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistItemResponse;
import com.finance.notification.watchlist.dto.WatchlistItemUpdateRequest;
import com.finance.notification.config.WatchlistManagementProperties;
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
import java.util.Objects;
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
    private final TrackedAssetRepository trackedAssetRepository;
    private final WatchlistManagementProperties managementProperties;

    @Transactional
    public WatchlistItemResponse addToList(Long watchlistId, String userSub,
                                           WatchlistItemCreateRequest request) {
        Watchlist parent = managementService.requireOwned(watchlistId, userSub);
        TrackedAsset trackedAsset = requireTrackedAsset(request.marketType(), request.assetCode());
        return repository.findByWatchlistIdAndTrackedAsset_Id(parent.getId(), trackedAsset.getId())
                .map(existing -> updateExisting(existing, request))
                .orElseGet(() -> createItem(parent, userSub, request, trackedAsset));
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
        return repository.findByTrackedAsset_AssetType(
                TrackedAssetType.valueOf(marketType.name()));
    }

    @Transactional
    public void persist(WatchlistItem item) {
        repository.save(item);
    }

    private WatchlistItemResponse updateExisting(WatchlistItem existing, WatchlistItemCreateRequest request) {
        boolean changed = false;
        if (request.deltaThreshold() != null
                && !Objects.equals(request.deltaThreshold(), existing.getDeltaThreshold())) {
            existing.setDeltaThreshold(request.deltaThreshold());
            changed = true;
        }
        if (request.note() != null) {
            String normalized = request.note().isBlank() ? null : request.note();
            if (!Objects.equals(normalized, existing.getNote())) {
                existing.setNote(normalized);
                changed = true;
            }
        }
        if (!changed) return mapper.toResponse(existing);
        WatchlistItem saved = repository.save(existing);
        log.info("Watchlist item upserted itemId={} userSub={} market={} code={} deltaThreshold={}",
                saved.getId(), saved.getUserSub(), saved.getMarketType(),
                saved.getAssetCode(), saved.getDeltaThreshold());
        return mapper.toResponse(saved);
    }

    private WatchlistItemResponse createItem(Watchlist parent, String userSub,
                                              WatchlistItemCreateRequest request,
                                              TrackedAsset trackedAsset) {
        int maxItems = managementProperties.maxItemsPerList();
        if (maxItems > 0 && repository.countByWatchlistId(parent.getId()) >= maxItems) {
            throw new BadRequestException("error.watchlist.itemMaxReached", maxItems);
        }
        WatchlistItem entity = mapper.toEntity(request, userSub);
        entity.setTrackedAsset(trackedAsset);
        entity.setAssetCode(trackedAsset.getAssetCode());
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

    private TrackedAsset requireTrackedAsset(MarketType marketType, String rawCode) {
        TrackedAssetType trackedType = TrackedAssetType.valueOf(marketType.name());
        String normalizedCode = trackedType.normalizeCode(rawCode);
        return trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(trackedType, normalizedCode)
                .orElseThrow(() -> new BusinessException(
                        "error.watchlist.assetNotTracked", marketType, normalizedCode));
    }

    private void validateReorder(List<WatchlistItem> existing, List<Long> itemIds, Long watchlistId) {
        if (itemIds.size() != existing.size()) {
            throw new BadRequestException("error.watchlist.reorderSizeMismatch", existing.size(), itemIds.size());
        }
        Set<Long> existingIds = existing.stream()
                .map(WatchlistItem::getId)
                .collect(Collectors.toCollection(HashSet::new));
        if (!new HashSet<>(itemIds).equals(existingIds)) {
            throw new BadRequestException("error.watchlist.reorderIdsMismatch", watchlistId);
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
                .orElseThrow(() -> new ResourceNotFoundException("error.watchlist.item.notFound", itemId));
    }
}
