package com.finance.notification.alert.service;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.notification.alert.dto.PriceAlertCreateRequest;
import com.finance.notification.alert.dto.PriceAlertResponse;
import com.finance.notification.alert.mapper.PriceAlertMapper;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.alert.model.PriceAlert;
import com.finance.notification.alert.repository.PriceAlertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceAlertServiceTest {

    @Mock private PriceAlertRepository repository;
    @Mock private PriceAlertMapper mapper;
    @Mock private com.finance.common.cache.AssetSnapshotCache assetSnapshotCache;
    @Mock private TrackedAssetRepository trackedAssetRepository;

    @InjectMocks
    private PriceAlertService service;

    private PriceAlert ownedAlert() {
        return PriceAlert.builder()
                .id(1L).userSub("user-1").marketType(MarketType.CRYPTO).assetCode("BTC")
                .direction(AlertDirection.ABOVE).threshold(BigDecimal.valueOf(100))
                .currency("TRY").active(true).build();
    }

    @Test
    void create_persistsMappedEntity() {
        PriceAlertCreateRequest req = new PriceAlertCreateRequest(
                MarketType.CRYPTO, "BTC", AlertDirection.ABOVE,
                BigDecimal.valueOf(100), "TRY", null);
        PriceAlert entity = ownedAlert();
        TrackedAsset tracked = TrackedAsset.builder()
                .id(7L).assetType(TrackedAssetType.CRYPTO).assetCode("btc").build();
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, "btc"))
                .thenReturn(Optional.of(tracked));
        when(mapper.toEntity(req, "user-1")).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toResponse(entity)).thenReturn(stubResponse(entity));

        PriceAlertResponse result = service.create("user-1", req);

        assertThat(result.assetCode()).isEqualTo("BTC");
        verify(repository).save(entity);
    }

    @Test
    void delete_removesAlertWhenOwned() {
        PriceAlert alert = ownedAlert();
        when(repository.findById(1L)).thenReturn(Optional.of(alert));

        service.delete(1L, "user-1");

        verify(repository).delete(alert);
    }

    @Test
    void delete_throws404ForOtherOwner() {
        PriceAlert alert = ownedAlert();
        when(repository.findById(1L)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> service.delete(1L, "intruder"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any(PriceAlert.class));
    }

    @Test
    void activeAlerts_delegatesToRepository() {
        when(repository.findByActiveTrueAndMarketType(MarketType.CRYPTO))
                .thenReturn(java.util.List.of(ownedAlert()));

        var result = service.activeAlerts(MarketType.CRYPTO);

        assertThat(result).hasSize(1);
    }

    private PriceAlertResponse stubResponse(PriceAlert a) {
        return new PriceAlertResponse(a.getId(), a.getMarketType(), a.getAssetCode(),
                null, null, null, null, null,
                a.getDirection(), a.getThreshold(), a.getCurrency(), a.getReferencePrice(),
                a.isActive(), a.getTriggeredAt(), a.getCreatedAt());
    }
}
