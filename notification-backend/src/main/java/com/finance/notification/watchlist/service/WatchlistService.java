package com.finance.notification.watchlist.service;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.notification.watchlist.dto.WatchlistItemCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistItemResponse;
import com.finance.notification.watchlist.mapper.WatchlistItemMapper;
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

    @Transactional
    public WatchlistItemResponse add(String userSub, WatchlistItemCreateRequest request) {
        return repository.findByUserSubAndMarketTypeAndAssetCode(userSub, request.marketType(), request.assetCode())
                .map(mapper::toResponse)
                .orElseGet(() -> mapper.toResponse(repository.save(mapper.toEntity(request, userSub))));
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> list(String userSub) {
        return repository.findByUserSubOrderByCreatedAtDesc(userSub).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public void remove(Long id, String userSub) {
        WatchlistItem item = ownedOr404(id, userSub);
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

    private WatchlistItem ownedOr404(Long id, String userSub) {
        return repository.findById(id)
                .filter(i -> i.belongsTo(userSub))
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist item not found id=" + id));
    }
}
