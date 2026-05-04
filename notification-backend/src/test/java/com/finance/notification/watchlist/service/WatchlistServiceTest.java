package com.finance.notification.watchlist.service;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.notification.watchlist.dto.WatchlistItemCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistItemResponse;
import com.finance.notification.watchlist.mapper.WatchlistItemMapper;
import com.finance.notification.watchlist.model.WatchlistItem;
import com.finance.notification.watchlist.repository.WatchlistItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock private WatchlistItemRepository repository;
    @Mock private WatchlistItemMapper mapper;

    @InjectMocks
    private WatchlistService service;

    private WatchlistItem ownedItem() {
        return WatchlistItem.builder()
                .id(1L).userSub("user-1").marketType(MarketType.CRYPTO).assetCode("BTC").build();
    }

    @Test
    void add_returnsExistingWhenSameAssetTrackedByUser() {
        WatchlistItem existing = ownedItem();
        WatchlistItemCreateRequest req = new WatchlistItemCreateRequest(
                MarketType.CRYPTO, "BTC", null, null);
        when(repository.findByUserSubAndMarketTypeAndAssetCode("user-1", MarketType.CRYPTO, "BTC"))
                .thenReturn(Optional.of(existing));
        when(mapper.toResponse(existing)).thenReturn(stub(existing));

        WatchlistItemResponse result = service.add("user-1", req);

        verify(repository, never()).save(any(WatchlistItem.class));
    }

    @Test
    void add_persistsWhenAssetNotYetTracked() {
        WatchlistItemCreateRequest req = new WatchlistItemCreateRequest(
                MarketType.CRYPTO, "BTC", "note", null);
        WatchlistItem entity = ownedItem();
        when(repository.findByUserSubAndMarketTypeAndAssetCode("user-1", MarketType.CRYPTO, "BTC"))
                .thenReturn(Optional.empty());
        when(mapper.toEntity(req, "user-1")).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toResponse(entity)).thenReturn(stub(entity));

        service.add("user-1", req);

        verify(repository).save(entity);
    }

    @Test
    void remove_deletesWhenOwned() {
        WatchlistItem item = ownedItem();
        when(repository.findById(1L)).thenReturn(Optional.of(item));

        service.remove(1L, "user-1");

        verify(repository).delete(item);
    }

    @Test
    void remove_throws404ForOtherOwner() {
        WatchlistItem item = ownedItem();
        when(repository.findById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.remove(1L, "intruder"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any(WatchlistItem.class));
    }

    private WatchlistItemResponse stub(WatchlistItem i) {
        return new WatchlistItemResponse(i.getId(), i.getMarketType(), i.getAssetCode(),
                i.getNote(), i.getDeltaThreshold(), i.getLastSeenPrice(), i.getLastSeenAt(),
                i.getCreatedAt());
    }
}
