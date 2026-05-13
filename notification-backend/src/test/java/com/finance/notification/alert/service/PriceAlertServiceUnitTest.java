package com.finance.notification.alert.service;

import com.finance.common.cache.AssetSnapshotCache;
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
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.alert.model.PriceAlert;
import com.finance.notification.alert.repository.PriceAlertRepository;
import com.finance.notification.config.PriceAlertProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceAlertServiceUnitTest {

    @Mock private PriceAlertRepository repository;
    @Mock private PriceAlertMapper mapper;
    @Mock private AssetSnapshotCache assetSnapshotCache;
    @Mock private TrackedAssetRepository trackedAssetRepository;

    private PriceAlertService service;

    @BeforeEach
    void setUp() {
        PriceAlertProperties properties = new PriceAlertProperties(50);
        service = new PriceAlertService(repository, mapper, assetSnapshotCache,
                trackedAssetRepository, properties);
    }

    private PriceAlertCreateRequest createRequest() {
        return new PriceAlertCreateRequest(MarketType.STOCK, "THYAO", AlertDirection.ABOVE,
                new BigDecimal("100"), "TRY", null);
    }

    private PriceAlert alert(String userSub) {
        return PriceAlert.builder()
                .userSub(userSub)
                .marketType(MarketType.STOCK)
                .assetCode("THYAO")
                .direction(AlertDirection.ABOVE)
                .threshold(new BigDecimal("100"))
                .active(true)
                .build();
    }

    private TrackedAsset tracked() {
        return TrackedAsset.builder()
                .assetType(TrackedAssetType.STOCK)
                .assetCode("THYAO")
                .build();
    }

    @Test
    void create_raises_whenUserReachedMaxAlerts() {
        when(repository.countByUserSub("user-1")).thenReturn(50L);

        assertThatThrownBy(() -> service.create("user-1", createRequest()))
                .isInstanceOf(BadRequestException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void create_raises_whenAssetNotTracked() {
        when(repository.countByUserSub("user-1")).thenReturn(0L);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(eq(TrackedAssetType.STOCK), anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("user-1", createRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_savesAlertAndReturnsResponse() {
        when(repository.countByUserSub("user-1")).thenReturn(0L);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(eq(TrackedAssetType.STOCK), anyString()))
                .thenReturn(Optional.of(tracked()));
        PriceAlert saved = alert("user-1");
        when(mapper.toEntity(any(), eq("user-1"))).thenReturn(saved);
        when(repository.save(saved)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(stubResponse());

        service.create("user-1", createRequest());

        verify(repository).save(saved);
    }

    @Test
    void list_returnsEmptyPage_whenAlertsEmpty() {
        when(repository.findByUserSubOrderByCreatedAtDesc(eq("user-1"), any(PageRequest.class)))
                .thenReturn(Page.empty());

        service.list("user-1", 0, 10);

        verify(assetSnapshotCache, never()).findByCodes(any(), any());
    }

    @Test
    void list_loadsSnapshotsAndMapsResponses_whenAlertsPresent() {
        PriceAlert a = alert("user-1");
        when(repository.findByUserSubOrderByCreatedAtDesc(eq("user-1"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(a)));
        when(assetSnapshotCache.findByCodes(eq(MarketType.STOCK), any())).thenReturn(Map.of());

        service.list("user-1", 0, 10);

        verify(assetSnapshotCache).findByCodes(eq(MarketType.STOCK), any());
    }

    @Test
    void delete_raises_whenAlertNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, "user-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_raises_whenAlertNotOwnedByUser() {
        PriceAlert a = alert("other-user");
        when(repository.findById(1L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.delete(1L, "user-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesAlert_whenOwnedByUser() {
        PriceAlert a = alert("user-1");
        when(repository.findById(1L)).thenReturn(Optional.of(a));

        service.delete(1L, "user-1");

        verify(repository).delete(a);
    }

    @Test
    void reactivate_callsReactivateAndSaves() {
        PriceAlert a = alert("user-1");
        when(repository.findById(1L)).thenReturn(Optional.of(a));
        when(repository.save(a)).thenReturn(a);
        when(mapper.toResponse(a)).thenReturn(stubResponse());

        service.reactivate(1L, "user-1");

        verify(repository).save(a);
    }

    @Test
    void update_appliesNewDirectionAndThreshold() {
        PriceAlert a = alert("user-1");
        when(repository.findById(1L)).thenReturn(Optional.of(a));
        when(repository.save(a)).thenReturn(a);
        when(mapper.toResponse(a)).thenReturn(stubResponse());

        service.update(1L, "user-1",
                new PriceAlertUpdateRequest(AlertDirection.BELOW, new BigDecimal("90")));

        verify(repository).save(a);
    }

    @Test
    void activeAlerts_delegatesToRepository() {
        when(repository.findByActiveTrueAndTrackedAsset_AssetType(TrackedAssetType.STOCK))
                .thenReturn(List.of(alert("user-1")));

        service.activeAlerts(MarketType.STOCK);

        verify(repository).findByActiveTrueAndTrackedAsset_AssetType(TrackedAssetType.STOCK);
    }

    @Test
    void persist_savesAlert() {
        PriceAlert a = alert("user-1");

        service.persist(a);

        verify(repository).save(a);
    }

    private PriceAlertResponse stubResponse() {
        return new PriceAlertResponse(1L, MarketType.STOCK, "THYAO", null, null,
                null, null, null, AlertDirection.ABOVE, new BigDecimal("100"),
                "TRY", null, true, null, null);
    }
}
