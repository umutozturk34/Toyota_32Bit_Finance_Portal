package com.finance.notification.watchlist.service;

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

import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistItemRepository repository;
    private final WatchlistItemMapper mapper;
    private final WatchlistManagementService managementService;

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
        return repository.findByWatchlistIdOrderByCreatedAtDesc(watchlistId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> listAllItems(String userSub) {
        return repository.findByUserSubOrderByCreatedAtDesc(userSub).stream()
                .map(mapper::toResponse)
                .toList();
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

    private WatchlistItem ownedItemOr404(Long itemId, String userSub) {
        return repository.findById(itemId)
                .filter(i -> i.belongsTo(userSub))
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist item not found id=" + itemId));
    }
}
