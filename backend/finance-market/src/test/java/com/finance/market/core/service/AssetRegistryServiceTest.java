package com.finance.market.core.service;

import com.finance.common.model.Instrument;
import com.finance.common.model.MarketType;
import com.finance.common.repository.InstrumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetRegistryServiceTest {

    @Mock
    private InstrumentRepository repository;

    @InjectMocks
    private AssetRegistryService service;

    @Test
    void upsertReturnsExistingInstrumentWithoutSavingWhenAlreadyRegistered() {
        // Arrange
        Instrument existing = Instrument.create(MarketType.FOREX, "EURUSD");
        when(repository.findByMarketTypeAndAssetCodeIgnoreCase(MarketType.FOREX, "EURUSD"))
                .thenReturn(Optional.of(existing));

        // Act
        Instrument result = service.upsert(MarketType.FOREX, "EURUSD");

        // Assert
        assertThat(result).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void upsertRegistersNewInstrumentWhenNotFound() {
        // Arrange
        when(repository.findByMarketTypeAndAssetCodeIgnoreCase(MarketType.STOCK, "AAPL"))
                .thenReturn(Optional.empty());
        Instrument saved = Instrument.create(MarketType.STOCK, "AAPL");
        when(repository.save(any(Instrument.class))).thenReturn(saved);

        // Act
        Instrument result = service.upsert(MarketType.STOCK, "AAPL");

        // Assert
        assertThat(result).isSameAs(saved);
        ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
        verify(repository).save(captor.capture());
        Instrument created = captor.getValue();
        assertThat(created.getMarketType()).isEqualTo(MarketType.STOCK);
        assertThat(created.getAssetCode()).isEqualTo("AAPL");
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void upsertPassesThroughCaseInsensitiveLookupArguments() {
        // Arrange
        when(repository.findByMarketTypeAndAssetCodeIgnoreCase(MarketType.CRYPTO, "btcusdt"))
                .thenReturn(Optional.empty());
        when(repository.save(any(Instrument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Instrument result = service.upsert(MarketType.CRYPTO, "btcusdt");

        // Assert
        assertThat(result.getMarketType()).isEqualTo(MarketType.CRYPTO);
        assertThat(result.getAssetCode()).isEqualTo("btcusdt");
        verify(repository).findByMarketTypeAndAssetCodeIgnoreCase(MarketType.CRYPTO, "btcusdt");
    }
}
