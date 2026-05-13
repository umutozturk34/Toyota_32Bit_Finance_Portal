package com.finance.notification.watchlist.service;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.finance.notification.watchlist.dto.WatchlistItemUpdateRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock private WatchlistItemRepository repository;
    @Mock private WatchlistItemMapper mapper;
    @Mock private WatchlistManagementService managementService;
    @Mock private com.finance.common.cache.AssetSnapshotCache assetSnapshotCache;
    @Mock private TrackedAssetRepository trackedAssetRepository;
    @Mock private com.finance.notification.config.WatchlistManagementProperties managementProperties;

    @InjectMocks
    private WatchlistService service;

    private TrackedAsset stubTrackedAsset(MarketType marketType, String code) {
        TrackedAssetType trackedType = TrackedAssetType.valueOf(marketType.name());
        String normalized = trackedType.normalizeCode(code);
        TrackedAsset asset = TrackedAsset.builder()
                .id(99L).assetType(trackedType).assetCode(normalized).build();
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(trackedType, normalized))
                .thenReturn(Optional.of(asset));
        return asset;
    }

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
        TrackedAsset tracked = stubTrackedAsset(MarketType.CRYPTO, "BTC");
        when(managementService.requireOwned(7L, "user-1")).thenReturn(parentList(7L, "user-1"));
        when(repository.findByWatchlistIdAndTrackedAsset_Id(7L, tracked.getId()))
                .thenReturn(Optional.of(existing));
        when(mapper.toResponse(existing)).thenReturn(stub(existing));

        service.addToList(7L, "user-1", req);

        verify(repository, never()).save(any(WatchlistItem.class));
    }

    @Test
    void addToList_updatesExistingWhenNewThresholdProvided() {
        WatchlistItemCreateRequest req = new WatchlistItemCreateRequest(
                MarketType.CRYPTO, "BTC", null, new BigDecimal("2.5"));
        WatchlistItem existing = ownedItem();
        TrackedAsset tracked = stubTrackedAsset(MarketType.CRYPTO, "BTC");
        when(managementService.requireOwned(7L, "user-1")).thenReturn(parentList(7L, "user-1"));
        when(repository.findByWatchlistIdAndTrackedAsset_Id(7L, tracked.getId()))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(WatchlistItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(WatchlistItem.class))).thenAnswer(inv -> stub(inv.getArgument(0)));

        service.addToList(7L, "user-1", req);

        ArgumentCaptor<WatchlistItem> captor = ArgumentCaptor.forClass(WatchlistItem.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDeltaThreshold()).isEqualByComparingTo("2.5");
    }

    @Test
    void addToList_persistsNewItemAttachedToTargetList() {
        WatchlistItemCreateRequest req = new WatchlistItemCreateRequest(
                MarketType.CRYPTO, "BTC", "note", null);
        WatchlistItem entity = ownedItem();
        TrackedAsset tracked = stubTrackedAsset(MarketType.CRYPTO, "BTC");
        when(managementService.requireOwned(7L, "user-1")).thenReturn(parentList(7L, "user-1"));
        when(repository.findByWatchlistIdAndTrackedAsset_Id(7L, tracked.getId()))
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
        TrackedAsset tracked = stubTrackedAsset(MarketType.STOCK, "AAPL");
        when(managementService.ensureDefault("user-1")).thenReturn(favorites);
        when(managementService.requireOwned(99L, "user-1")).thenReturn(favorites);
        when(repository.findByWatchlistIdAndTrackedAsset_Id(99L, tracked.getId()))
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
                null, null, null, null, null,
                i.getNote(), i.getDeltaThreshold(), i.getLastSeenPrice(), i.getLastSeenAt(),
                i.getCreatedAt());
    }

    @org.junit.jupiter.api.Test
    void addToList_throwsBusinessException_whenAssetNotTracked() {
        WatchlistItemCreateRequest req = new WatchlistItemCreateRequest(
                MarketType.CRYPTO, "UNKNOWN", null, null);
        com.finance.common.model.TrackedAssetType type =
                com.finance.common.model.TrackedAssetType.valueOf(MarketType.CRYPTO.name());
        String normalized = type.normalizeCode("UNKNOWN");
        when(trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalized))
                .thenReturn(Optional.empty());
        when(managementService.requireOwned(7L, "user-1")).thenReturn(parentList(7L, "user-1"));

        assertThatThrownBy(() -> service.addToList(7L, "user-1", req))
                .isInstanceOf(com.finance.common.exception.BusinessException.class);
    }

    @org.junit.jupiter.api.Test
    void addToList_throwsBadRequest_whenItemCountAtMax() {
        WatchlistItemCreateRequest req = new WatchlistItemCreateRequest(
                MarketType.CRYPTO, "BTC", null, null);
        TrackedAsset tracked = stubTrackedAsset(MarketType.CRYPTO, "BTC");
        when(managementService.requireOwned(7L, "user-1")).thenReturn(parentList(7L, "user-1"));
        when(repository.findByWatchlistIdAndTrackedAsset_Id(7L, tracked.getId()))
                .thenReturn(Optional.empty());
        when(managementProperties.maxItemsPerList()).thenReturn(5);
        when(repository.countByWatchlistId(7L)).thenReturn(5L);

        assertThatThrownBy(() -> service.addToList(7L, "user-1", req))
                .isInstanceOf(com.finance.common.exception.BadRequestException.class);
    }

    @org.junit.jupiter.api.Test
    void addToList_skipsSave_whenUpdatePayloadMatchesExisting() {
        WatchlistItemCreateRequest req = new WatchlistItemCreateRequest(
                MarketType.CRYPTO, "BTC", "  ", null);
        WatchlistItem existing = ownedItem();
        TrackedAsset tracked = stubTrackedAsset(MarketType.CRYPTO, "BTC");
        when(managementService.requireOwned(7L, "user-1")).thenReturn(parentList(7L, "user-1"));
        when(repository.findByWatchlistIdAndTrackedAsset_Id(7L, tracked.getId()))
                .thenReturn(Optional.of(existing));
        when(mapper.toResponse(existing)).thenReturn(stub(existing));

        service.addToList(7L, "user-1", req);

        verify(repository, never()).save(any(WatchlistItem.class));
    }

    @org.junit.jupiter.api.Test
    void listItems_returnsDbSortedItems_forSortableField() {
        WatchlistItem item = ownedItem();
        when(managementService.requireOwned(7L, "user-1")).thenReturn(parentList(7L, "user-1"));
        when(repository.findByWatchlistId(eq(7L), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(item));
        when(assetSnapshotCache.findByCodes(any(MarketType.class), any())).thenReturn(java.util.Map.of());
        when(mapper.toResponse(eq(item), any())).thenReturn(stub(item));

        List<WatchlistItemResponse> result = service.listItems(7L, "user-1",
                com.finance.notification.watchlist.model.WatchlistSortBy.CUSTOM,
                org.springframework.data.domain.Sort.Direction.ASC);

        assertThat(result).hasSize(1);
    }

    @org.junit.jupiter.api.Test
    void listAllItems_returnsEnrichedFromRepository() {
        WatchlistItem item = ownedItem();
        when(repository.findByUserSubOrderByCreatedAtDesc("user-1")).thenReturn(List.of(item));
        when(assetSnapshotCache.findByCodes(any(MarketType.class), any())).thenReturn(java.util.Map.of());
        when(mapper.toResponse(eq(item), any())).thenReturn(stub(item));

        List<WatchlistItemResponse> result = service.listAllItems("user-1");

        assertThat(result).hasSize(1);
    }

    @org.junit.jupiter.api.Test
    void listAllItems_returnsEmptyList_whenNoItems() {
        when(repository.findByUserSubOrderByCreatedAtDesc("user-1")).thenReturn(List.of());

        List<WatchlistItemResponse> result = service.listAllItems("user-1");

        assertThat(result).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void reorder_savesNewDisplayOrders_andReturnsEnriched() {
        WatchlistItem a = WatchlistItem.builder().id(1L).userSub("user-1").watchlistId(7L)
                .marketType(MarketType.CRYPTO).assetCode("BTC").displayOrder(1).build();
        WatchlistItem b = WatchlistItem.builder().id(2L).userSub("user-1").watchlistId(7L)
                .marketType(MarketType.CRYPTO).assetCode("ETH").displayOrder(2).build();
        when(managementService.requireOwned(7L, "user-1")).thenReturn(parentList(7L, "user-1"));
        when(repository.findByWatchlistId(eq(7L), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(new java.util.ArrayList<>(List.of(a, b)));
        when(assetSnapshotCache.findByCodes(any(MarketType.class), any())).thenReturn(java.util.Map.of());
        when(mapper.toResponse(any(WatchlistItem.class), any())).thenAnswer(inv -> stub(inv.getArgument(0)));

        service.reorder(7L, "user-1", List.of(2L, 1L));

        assertThat(b.getDisplayOrder()).isEqualTo(1);
        assertThat(a.getDisplayOrder()).isEqualTo(2);
        verify(repository).saveAll(any());
    }

    @org.junit.jupiter.api.Test
    void reorder_throwsBadRequest_whenSizeMismatch() {
        WatchlistItem a = WatchlistItem.builder().id(1L).build();
        WatchlistItem b = WatchlistItem.builder().id(2L).build();
        when(managementService.requireOwned(7L, "user-1")).thenReturn(parentList(7L, "user-1"));
        when(repository.findByWatchlistId(eq(7L), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(a, b));

        assertThatThrownBy(() -> service.reorder(7L, "user-1", List.of(1L)))
                .isInstanceOf(com.finance.common.exception.BadRequestException.class);
    }

    @org.junit.jupiter.api.Test
    void reorder_throwsBadRequest_whenIdsDoNotMatch() {
        WatchlistItem a = WatchlistItem.builder().id(1L).build();
        WatchlistItem b = WatchlistItem.builder().id(2L).build();
        when(managementService.requireOwned(7L, "user-1")).thenReturn(parentList(7L, "user-1"));
        when(repository.findByWatchlistId(eq(7L), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(a, b));

        assertThatThrownBy(() -> service.reorder(7L, "user-1", List.of(1L, 99L)))
                .isInstanceOf(com.finance.common.exception.BadRequestException.class);
    }

    @org.junit.jupiter.api.Test
    void updateItem_persistsNoteAndThreshold_andReturnsEnriched() {
        WatchlistItem item = ownedItem();
        WatchlistItemUpdateRequest req = new WatchlistItemUpdateRequest("new", new BigDecimal("3"));
        when(repository.findById(1L)).thenReturn(Optional.of(item));
        when(repository.save(item)).thenReturn(item);
        when(assetSnapshotCache.findByCodes(any(MarketType.class), any())).thenReturn(java.util.Map.of());
        when(mapper.toResponse(eq(item), any())).thenReturn(stub(item));

        service.updateItem(1L, "user-1", req);

        assertThat(item.getNote()).isEqualTo("new");
        assertThat(item.getDeltaThreshold()).isEqualByComparingTo("3");
    }

    @org.junit.jupiter.api.Test
    void updateItem_blanksNote_whenStringEmpty() {
        WatchlistItem item = ownedItem();
        item.setNote("old");
        WatchlistItemUpdateRequest req = new WatchlistItemUpdateRequest("   ", null);
        when(repository.findById(1L)).thenReturn(Optional.of(item));
        when(repository.save(item)).thenReturn(item);
        when(assetSnapshotCache.findByCodes(any(MarketType.class), any())).thenReturn(java.util.Map.of());
        when(mapper.toResponse(eq(item), any())).thenReturn(stub(item));

        service.updateItem(1L, "user-1", req);

        assertThat(item.getNote()).isNull();
    }

    @org.junit.jupiter.api.Test
    void updateItem_throwsResourceNotFound_whenOtherOwner() {
        WatchlistItem item = ownedItem();
        WatchlistItemUpdateRequest req = new WatchlistItemUpdateRequest("x", null);
        when(repository.findById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.updateItem(1L, "intruder", req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @org.junit.jupiter.api.Test
    void itemsForMarket_delegatesToRepositoryByTrackedAssetType() {
        WatchlistItem item = ownedItem();
        when(repository.findByTrackedAsset_AssetType(
                com.finance.common.model.TrackedAssetType.CRYPTO))
                .thenReturn(List.of(item));

        List<WatchlistItem> result = service.itemsForMarket(MarketType.CRYPTO);

        assertThat(result).containsExactly(item);
    }

    @org.junit.jupiter.api.Test
    void persist_invokesRepositorySave() {
        WatchlistItem item = ownedItem();

        service.persist(item);

        verify(repository).save(item);
    }
}
