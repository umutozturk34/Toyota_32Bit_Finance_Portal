package com.finance.market.crypto.service;

import com.finance.common.model.MarketType;
import com.finance.common.model.Asset;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.crypto.dto.external.CoinGeckoCandleDto;
import com.finance.market.crypto.dto.external.CoinGeckoSnapshotDto;
import com.finance.market.crypto.mapper.CryptoMapper;
import com.finance.market.crypto.model.Crypto;
import com.finance.market.crypto.model.CryptoCandle;
import com.finance.market.crypto.repository.CryptoCandleRepository;
import com.finance.market.crypto.repository.CryptoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoEntityWriterTest {

    private static final String COIN_ID = "bitcoin";

    @Mock private CryptoRepository cryptoRepository;
    @Mock private CryptoCandleRepository cryptoCandleRepository;
    @Mock private CryptoMapper cryptoMapper;
    @Mock private AssetRegistryService assetRegistry;

    private CryptoEntityWriter writer;

    @BeforeEach
    void setUp() {
        writer = new CryptoEntityWriter(cryptoRepository, cryptoCandleRepository, cryptoMapper, assetRegistry);
    }

    @Test
    void saveSnapshot_createsNewEntity_whenNoExistingRowFound() {
        CoinGeckoSnapshotDto dto = snapshotDto();
        BigDecimal tryPrice = new BigDecimal("3500");
        Crypto fresh = cryptoWithId();
        Asset asset = org.mockito.Mockito.mock(Asset.class);
        when(cryptoRepository.findById(COIN_ID)).thenReturn(Optional.empty());
        when(cryptoMapper.toEntity(any(CoinGeckoSnapshotDto.class), any(BigDecimal.class), any(LocalDateTime.class)))
                .thenReturn(fresh);
        when(assetRegistry.upsert(MarketType.CRYPTO, COIN_ID)).thenReturn(asset);

        Crypto result = writer.saveSnapshot(dto, tryPrice);

        assertThat(result).isSameAs(fresh);
        assertThat(fresh.getAsset()).isSameAs(asset);
        verify(cryptoRepository).save(fresh);
        verify(cryptoMapper, never()).updateEntityFromDto(any(), any(), any(), any());
    }

    @Test
    void saveSnapshot_updatesExistingEntity_whenRowFound() {
        CoinGeckoSnapshotDto dto = snapshotDto();
        BigDecimal tryPrice = new BigDecimal("3500");
        Crypto existing = cryptoWithId();
        Asset asset = org.mockito.Mockito.mock(Asset.class);
        when(cryptoRepository.findById(COIN_ID)).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.CRYPTO, COIN_ID)).thenReturn(asset);

        Crypto result = writer.saveSnapshot(dto, tryPrice);

        assertThat(result).isSameAs(existing);
        assertThat(existing.getAsset()).isSameAs(asset);
        verify(cryptoMapper).updateEntityFromDto(any(Crypto.class), any(CoinGeckoSnapshotDto.class),
                any(BigDecimal.class), any(LocalDateTime.class));
        verify(cryptoMapper, never()).toEntity(any(), any(), any());
        verify(cryptoRepository).save(existing);
    }

    @Test
    void replaceCandleHistory_deletesByCoinIdThenSavesAll() {
        List<CryptoCandle> candles = List.of(new CryptoCandle(), new CryptoCandle());

        writer.replaceCandleHistory(COIN_ID, candles);

        verify(cryptoCandleRepository).deleteByCryptoId(COIN_ID);
        verify(cryptoCandleRepository).saveAll(candles);
    }

    @Test
    void upsertCandles_savesOnlyNewEntities_whenAllDtosAreNew() {
        Crypto crypto = Crypto.builder().build();
        CoinGeckoCandleDto dto = candleDto(LocalDateTime.of(2026, 1, 5, 0, 0));
        CryptoCandle newEntity = new CryptoCandle();
        when(cryptoCandleRepository.findByCryptoIdAndCandleDateIn(any(), anyList())).thenReturn(List.of());
        when(cryptoMapper.toCandleEntity(dto, crypto)).thenReturn(newEntity);

        int changed = writer.upsertCandles(COIN_ID, crypto, List.of(dto));

        assertThat(changed).isEqualTo(1);
        ArgumentCaptor<List<CryptoCandle>> captor = ArgumentCaptor.forClass(List.class);
        verify(cryptoCandleRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void upsertCandles_updatesExistingAndSkipsSaveAll_whenAllExist() {
        Crypto crypto = Crypto.builder().build();
        LocalDateTime ts = LocalDateTime.of(2026, 1, 5, 0, 0);
        CoinGeckoCandleDto dto = candleDto(ts);
        CryptoCandle existing = new CryptoCandle();
        existing.setCandleDate(ts);
        when(cryptoCandleRepository.findByCryptoIdAndCandleDateIn(any(), anyList())).thenReturn(List.of(existing));

        int changed = writer.upsertCandles(COIN_ID, crypto, List.of(dto));

        assertThat(changed).isEqualTo(1);
        verify(cryptoMapper).updateCandleEntity(existing, dto);
        verify(cryptoCandleRepository, never()).saveAll(anyList());
    }

    @Test
    void upsertCandles_returnsZero_whenDtosEmpty() {
        Crypto crypto = Crypto.builder().build();
        when(cryptoCandleRepository.findByCryptoIdAndCandleDateIn(any(), anyList())).thenReturn(List.of());

        int changed = writer.upsertCandles(COIN_ID, crypto, List.of());

        assertThat(changed).isZero();
        verify(cryptoCandleRepository, never()).saveAll(anyList());
    }

    private Crypto cryptoWithId() {
        Crypto c = Crypto.builder().build();
        c.setId(COIN_ID);
        return c;
    }

    private CoinGeckoSnapshotDto snapshotDto() {
        return new CoinGeckoSnapshotDto(COIN_ID, "btc", "Bitcoin", "img",
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000"), new BigDecimal("100"));
    }

    private CoinGeckoCandleDto candleDto(LocalDateTime ts) {
        return new CoinGeckoCandleDto(COIN_ID, ts,
                new BigDecimal("100"), new BigDecimal("101"),
                new BigDecimal("99"), new BigDecimal("100"), 1000L);
    }
}
