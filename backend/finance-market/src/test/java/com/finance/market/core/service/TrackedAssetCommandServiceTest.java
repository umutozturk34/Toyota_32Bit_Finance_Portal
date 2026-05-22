package com.finance.market.core.service;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.Instrument;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.core.dto.request.TrackedAssetOrderItemRequest;
import com.finance.market.core.dto.response.TrackedAssetResponse;
import com.finance.market.core.mapper.TrackedAssetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackedAssetCommandServiceTest {

    @Mock private TrackedAssetRepository trackedAssetRepository;
    @Mock private TrackedAssetMapper trackedAssetMapper;
    @Mock private TrackedAssetCodeCache codeCache;
    @Mock private AssetRegistryService assetRegistry;

    private TrackedAssetCommandService service;

    @BeforeEach
    void setUp() {
        service = new TrackedAssetCommandService(trackedAssetRepository, trackedAssetMapper,
                codeCache, assetRegistry);
    }

    private TrackedAssetUpsertCommand cmd(String code, String displayName, Integer sortOrder) {
        return TrackedAssetUpsertCommand.builder()
                .assetType(TrackedAssetType.CRYPTO)
                .assetCode(code)
                .displayName(displayName)
                .binanceSymbol("BTCUSDT")
                .sortOrder(sortOrder)
                .build();
    }

    private TrackedAsset existing(String code, String displayName) {
        TrackedAsset a = TrackedAsset.builder()
                .assetType(TrackedAssetType.CRYPTO)
                .assetCode(code)
                .displayName(displayName)
                .build();
        return a;
    }

    private Instrument asset(MarketType type, String code) {
        return Instrument.builder().marketType(type).assetCode(code).build();
    }

    @Test
    void upsert_createsNewEntity_whenRepositoryEmpty() {
        TrackedAssetUpsertCommand command = cmd("bitcoin", "Bitcoin", 5);
        Instrument asset = asset(MarketType.CRYPTO, "bitcoin");
        when(assetRegistry.upsert(MarketType.CRYPTO, "bitcoin")).thenReturn(asset);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, "bitcoin"))
                .thenReturn(Optional.empty());
        TrackedAsset persisted = existing("bitcoin", "Bitcoin");
        when(trackedAssetRepository.save(any(TrackedAsset.class))).thenReturn(persisted);
        when(trackedAssetMapper.toResponse(persisted))
                .thenReturn(TrackedAssetResponse.builder().assetCode("bitcoin").build());

        TrackedAssetResponse result = service.upsert(command);

        assertThat(result.getAssetCode()).isEqualTo("bitcoin");
        verify(codeCache).invalidate(TrackedAssetType.CRYPTO);
    }

    @Test
    void upsert_keepsExistingDisplayName_whenCommandDisplayNameIsNull() {
        TrackedAssetUpsertCommand command = cmd("bitcoin", null, 1);
        Instrument asset = asset(MarketType.CRYPTO, "bitcoin");
        TrackedAsset previous = existing("bitcoin", "Already set");
        when(assetRegistry.upsert(MarketType.CRYPTO, "bitcoin")).thenReturn(asset);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, "bitcoin"))
                .thenReturn(Optional.of(previous));
        when(trackedAssetRepository.save(any(TrackedAsset.class))).thenReturn(previous);
        when(trackedAssetMapper.toResponse(previous))
                .thenReturn(TrackedAssetResponse.builder().displayName("Already set").build());

        TrackedAssetResponse result = service.upsert(command);

        assertThat(result.getDisplayName()).isEqualTo("Already set");
    }

    @Test
    void upsert_clearsDisplayName_whenCommandDisplayNameBlank() {
        TrackedAssetUpsertCommand command = cmd("bitcoin", "   ", 1);
        Instrument asset = asset(MarketType.CRYPTO, "bitcoin");
        TrackedAsset previous = existing("bitcoin", "Old");
        when(assetRegistry.upsert(MarketType.CRYPTO, "bitcoin")).thenReturn(asset);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, "bitcoin"))
                .thenReturn(Optional.of(previous));
        when(trackedAssetRepository.save(any(TrackedAsset.class))).thenReturn(previous);
        when(trackedAssetMapper.toResponse(previous))
                .thenReturn(TrackedAssetResponse.builder().displayName(null).build());

        service.upsert(command);

        assertThat(previous.getDisplayName()).isNull();
    }

    @Test
    void upsert_trimsDisplayName_whenCommandHasWhitespace() {
        TrackedAssetUpsertCommand command = cmd("bitcoin", "  Bitcoin  ", 1);
        Instrument asset = asset(MarketType.CRYPTO, "bitcoin");
        TrackedAsset previous = existing("bitcoin", "Old");
        when(assetRegistry.upsert(MarketType.CRYPTO, "bitcoin")).thenReturn(asset);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, "bitcoin"))
                .thenReturn(Optional.of(previous));
        when(trackedAssetRepository.save(any(TrackedAsset.class))).thenReturn(previous);
        when(trackedAssetMapper.toResponse(previous))
                .thenReturn(TrackedAssetResponse.builder().displayName("Bitcoin").build());

        service.upsert(command);

        assertThat(previous.getDisplayName()).isEqualTo("Bitcoin");
    }

    @Test
    void upsert_defaultsSortOrderToZero_whenCommandSortOrderIsNull() {
        TrackedAssetUpsertCommand command = cmd("bitcoin", "Bitcoin", null);
        Instrument asset = asset(MarketType.CRYPTO, "bitcoin");
        when(assetRegistry.upsert(MarketType.CRYPTO, "bitcoin")).thenReturn(asset);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, "bitcoin"))
                .thenReturn(Optional.empty());
        ArgumentCaptor<TrackedAsset> captor = ArgumentCaptor.forClass(TrackedAsset.class);
        when(trackedAssetRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(trackedAssetMapper.toResponse(any())).thenReturn(TrackedAssetResponse.builder().build());

        service.upsert(command);

        assertThat(captor.getValue().getSortOrder()).isZero();
    }

    @Test
    void autoTrack_savesEntity_whenAssetNotYetTracked() {
        Instrument asset = asset(MarketType.CRYPTO, "bitcoin");
        when(assetRegistry.upsert(MarketType.CRYPTO, "bitcoin")).thenReturn(asset);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, "bitcoin"))
                .thenReturn(Optional.empty());

        service.autoTrack(TrackedAssetType.CRYPTO, "bitcoin", "Bitcoin", 5);

        verify(trackedAssetRepository).save(any(TrackedAsset.class));
        verify(codeCache).invalidate(TrackedAssetType.CRYPTO);
    }

    @Test
    void autoTrack_skipsSave_whenAssetAlreadyTracked() {
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, "bitcoin"))
                .thenReturn(Optional.of(existing("bitcoin", "Bitcoin")));

        service.autoTrack(TrackedAssetType.CRYPTO, "bitcoin", "Bitcoin", 5);

        verify(trackedAssetRepository, never()).save(any());
        verify(codeCache, never()).invalidate(any());
    }

    @Test
    void delete_removesEntity_andInvalidatesCache() {
        TrackedAsset target = existing("bitcoin", "Bitcoin");
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, "bitcoin"))
                .thenReturn(Optional.of(target));

        service.delete(TrackedAssetType.CRYPTO, "bitcoin");

        verify(trackedAssetRepository).delete(target);
        verify(codeCache).invalidate(TrackedAssetType.CRYPTO);
    }

    @Test
    void delete_raises_whenAssetMissing() {
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(eq(TrackedAssetType.CRYPTO), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(TrackedAssetType.CRYPTO, "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(codeCache, never()).invalidate(any());
    }

    @Test
    void updateSortOrders_skipsRun_whenItemListEmpty() {
        service.updateSortOrders(TrackedAssetType.CRYPTO, List.of());

        verify(trackedAssetRepository, never()).saveAll(any());
        verify(codeCache, never()).invalidate(any());
    }

    @Test
    void updateSortOrders_skipsRun_whenItemListNull() {
        service.updateSortOrders(TrackedAssetType.CRYPTO, null);

        verify(trackedAssetRepository, never()).saveAll(any());
    }

    @Test
    void updateSortOrders_appliesNewOrders_whenAllAssetsResolve() {
        TrackedAsset btc = existing("bitcoin", "Bitcoin");
        TrackedAsset eth = existing("ethereum", "Ethereum");
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, "bitcoin"))
                .thenReturn(Optional.of(btc));
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, "ethereum"))
                .thenReturn(Optional.of(eth));

        service.updateSortOrders(TrackedAssetType.CRYPTO, List.of(
                new TrackedAssetOrderItemRequest("bitcoin", 1),
                new TrackedAssetOrderItemRequest("ethereum", 2)));

        assertThat(btc.getSortOrder()).isEqualTo(1);
        assertThat(eth.getSortOrder()).isEqualTo(2);
        verify(trackedAssetRepository).saveAll(List.of(btc, eth));
        verify(codeCache).invalidate(TrackedAssetType.CRYPTO);
    }

    @Test
    void updateSortOrders_raises_whenAnyAssetMissing() {
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(eq(TrackedAssetType.CRYPTO), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSortOrders(TrackedAssetType.CRYPTO,
                List.of(new TrackedAssetOrderItemRequest("missing", 1))))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
