package com.finance.common.service;

import com.finance.common.model.Asset;
import com.finance.common.model.MarketType;
import com.finance.common.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetRegistryServiceTest {

    @Mock private AssetRepository repository;

    private AssetRegistryService service;

    @BeforeEach
    void setUp() {
        service = new AssetRegistryService(repository);
    }

    @Test
    void should_createNewAsset_when_notFoundForMarketAndCode() {
        when(repository.findByMarketTypeAndAssetCodeIgnoreCase(MarketType.STOCK, "THYAO.IS"))
                .thenReturn(Optional.empty());
        Asset created = Asset.create(MarketType.STOCK, "THYAO.IS", "Türk Hava Yolları");
        when(repository.save(any(Asset.class))).thenReturn(created);

        Asset result = service.upsert(MarketType.STOCK, "THYAO.IS", "Türk Hava Yolları");

        assertThat(result).isSameAs(created);
        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMarketType()).isEqualTo(MarketType.STOCK);
        assertThat(captor.getValue().getAssetCode()).isEqualTo("THYAO.IS");
        assertThat(captor.getValue().getDisplayName()).isEqualTo("Türk Hava Yolları");
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void should_returnExistingAsset_when_foundForMarketAndCode() {
        Asset existing = Asset.create(MarketType.CRYPTO, "BTC", "Bitcoin");
        when(repository.findByMarketTypeAndAssetCodeIgnoreCase(MarketType.CRYPTO, "BTC"))
                .thenReturn(Optional.of(existing));

        Asset result = service.upsert(MarketType.CRYPTO, "BTC", "Bitcoin");

        assertThat(result).isSameAs(existing);
        verify(repository, never()).save(any(Asset.class));
    }

    @Test
    void should_updateDisplayName_when_existingAssetHasDifferentName() {
        Asset existing = Asset.create(MarketType.FOREX, "USDTRY", "USD/TRY");
        when(repository.findByMarketTypeAndAssetCodeIgnoreCase(MarketType.FOREX, "USDTRY"))
                .thenReturn(Optional.of(existing));

        Asset result = service.upsert(MarketType.FOREX, "USDTRY", "Amerikan Doları");

        assertThat(result.getDisplayName()).isEqualTo("Amerikan Doları");
        verify(repository, never()).save(any(Asset.class));
    }

    @Test
    void should_skipDisplayNameUpdate_when_newNameIsNull() {
        Asset existing = Asset.create(MarketType.FUND, "AAK", "Ak Portföy");
        when(repository.findByMarketTypeAndAssetCodeIgnoreCase(MarketType.FUND, "AAK"))
                .thenReturn(Optional.of(existing));

        Asset result = service.upsert(MarketType.FUND, "AAK", null);

        assertThat(result.getDisplayName()).isEqualTo("Ak Portföy");
    }

    @Test
    void should_returnAsset_when_requireOneFindsRow() {
        Asset existing = Asset.create(MarketType.COMMODITY, "XAUTRYG", "Gram Altın");
        when(repository.findByMarketTypeAndAssetCodeIgnoreCase(MarketType.COMMODITY, "XAUTRYG"))
                .thenReturn(Optional.of(existing));

        Asset result = service.requireOne(MarketType.COMMODITY, "XAUTRYG");

        assertThat(result).isSameAs(existing);
    }

    @Test
    void should_throwIllegalState_when_requireOneCannotFindRow() {
        when(repository.findByMarketTypeAndAssetCodeIgnoreCase(MarketType.BOND, "TRT051125T11"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOne(MarketType.BOND, "TRT051125T11"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOND")
                .hasMessageContaining("TRT051125T11");
        verify(repository, times(1)).findByMarketTypeAndAssetCodeIgnoreCase(any(), any());
    }
}
