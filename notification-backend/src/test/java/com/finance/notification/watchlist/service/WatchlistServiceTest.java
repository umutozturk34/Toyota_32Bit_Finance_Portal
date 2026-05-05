package com.finance.notification.watchlist.service;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.notification.watchlist.dto.WatchlistItemCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistItemResponse;
import com.finance.notification.watchlist.mapper.WatchlistItemMapper;
import com.finance.notification.watchlist.model.Watchlist;
import com.finance.notification.watchlist.model.WatchlistItem;
import com.finance.notification.watchlist.repository.WatchlistItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock private WatchlistItemRepository repository;
    @Mock private WatchlistItemMapper mapper;
    @Mock private WatchlistManagementService managementService;
    @Mock private com.finance.common.cache.AssetSnapshotCache assetSnapshotCache;

    @InjectMocks
    private WatchlistService service;

    private Watchlist parentList(Long id, String userSub) {
        return Watchlist.builder().id(id).userSub(userSub).name("Favoriler").isDefault(true).build();
    }

    private WatchlistItem ownedItem() {
        return WatchlistItem.builder()
                .id(1L).userSub("user-1").watchlistId(7L)
                .marketType(MarketType.CRYPTO).assetCode("BTC").build();
    }

    @Test
    void addToList_returnsExistingWhenAssetAlreadyTrackedInTargetList() {
        WatchlistItemCreateRequest req = new WatchlistItemCreateRequest(
                MarketType.CRYPTO, "BTC", null, null);
        WatchlistItem existing = ownedItem();
        when(managementService.requireOwned(7L, "user-1")).thenReturn(parentList(7L, "user-1"));
        when(repository.findByWatchlistIdAndMarketTypeAndAssetCode(7L, MarketType.CRYPTO, "BTC"))
                .thenReturn(Optional.of(existing));
        when(mapper.toResponse(existing)).thenReturn(stub(existing));

        service.addToList(7L, "user-1", req);

        verify(repository, never()).save(any(WatchlistItem.class));
    }

    @Test
    void addToList_persistsNewItemAttachedToTargetList() {
        WatchlistItemCreateRequest req = new WatchlistItemCreateRequest(
                MarketType.CRYPTO, "BTC", "note", null);
        WatchlistItem entity = ownedItem();
        when(managementService.requireOwned(7L, "user-1")).thenReturn(parentList(7L, "user-1"));
        when(repository.findByWatchlistIdAndMarketTypeAndAssetCode(7L, MarketType.CRYPTO, "BTC"))
                .thenReturn(Optional.empty());
        when(mapper.toEntity(req, "user-1")).thenReturn(entity);
        when(repository.save(any(WatchlistItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(WatchlistItem.class))).thenAnswer(inv -> stub(inv.getArgument(0)));

        service.addToList(7L, "user-1", req);

        ArgumentCaptor<WatchlistItem> captor = ArgumentCaptor.forClass(WatchlistItem.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getWatchlistId()).isEqualTo(7L);
    }

    @Test
    void addToDefault_resolvesDefaultListAndDelegates() {
        WatchlistItemCreateRequest req = new WatchlistItemCreateRequest(
                MarketType.STOCK, "AAPL", null, null);
        Watchlist favorites = parentList(99L, "user-1");
        when(managementService.ensureDefault("user-1")).thenReturn(favorites);
        when(managementService.requireOwned(99L, "user-1")).thenReturn(favorites);
        when(repository.findByWatchlistIdAndMarketTypeAndAssetCode(99L, MarketType.STOCK, "AAPL"))
                .thenReturn(Optional.empty());
        WatchlistItem entity = WatchlistItem.builder()
                .userSub("user-1").marketType(MarketType.STOCK).assetCode("AAPL").build();
        when(mapper.toEntity(req, "user-1")).thenReturn(entity);
        when(repository.save(any(WatchlistItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(WatchlistItem.class))).thenAnswer(inv -> stub(inv.getArgument(0)));

        service.addToDefault("user-1", req);

        ArgumentCaptor<WatchlistItem> captor = ArgumentCaptor.forClass(WatchlistItem.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getWatchlistId()).isEqualTo(99L);
    }

    @Test
    void removeItem_deletesWhenOwned() {
        WatchlistItem item = ownedItem();
        when(repository.findById(1L)).thenReturn(Optional.of(item));

        service.removeItem(1L, "user-1");

        verify(repository).delete(item);
    }

    @Test
    void removeItem_throws404ForOtherOwner() {
        WatchlistItem item = ownedItem();
        when(repository.findById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.removeItem(1L, "intruder"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any(WatchlistItem.class));
    }

    private WatchlistItemResponse stub(WatchlistItem i) {
        return new WatchlistItemResponse(i.getId(), i.getMarketType(), i.getAssetCode(),
                null, null, null,
                i.getNote(), i.getDeltaThreshold(), i.getLastSeenPrice(), i.getLastSeenAt(),
                i.getCreatedAt());
    }
}
