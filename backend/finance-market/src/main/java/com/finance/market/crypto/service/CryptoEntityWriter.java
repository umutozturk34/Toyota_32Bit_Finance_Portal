package com.finance.market.crypto.service;
import com.finance.common.service.MarketEntityWriter;


import com.finance.common.model.MarketType;
import com.finance.common.service.AssetRegistryService;
import com.finance.market.crypto.dto.external.CoinGeckoCandleDto;
import com.finance.market.crypto.dto.external.CoinGeckoSnapshotDto;
import com.finance.market.crypto.mapper.CryptoMapper;
import com.finance.market.crypto.model.Crypto;
import com.finance.market.crypto.model.CryptoCandle;
import com.finance.market.crypto.repository.CryptoCandleRepository;
import com.finance.market.crypto.repository.CryptoRepository;
import com.finance.common.util.CandleBatchUpsertTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Log4j2
@Component
@RequiredArgsConstructor
public class CryptoEntityWriter implements MarketEntityWriter {

    private final CryptoRepository cryptoRepository;
    private final CryptoCandleRepository cryptoCandleRepository;
    private final CryptoMapper cryptoMapper;
    private final AssetRegistryService assetRegistry;

    public Crypto saveSnapshot(CoinGeckoSnapshotDto usdDto, BigDecimal tryPrice) {
        LocalDateTime now = LocalDateTime.now();
        Crypto existing = cryptoRepository.findById(usdDto.id()).orElse(null);
        Crypto toPersist;
        if (existing != null) {
            cryptoMapper.updateEntityFromDto(existing, usdDto, tryPrice, now);
            toPersist = existing;
        } else {
            toPersist = cryptoMapper.toEntity(usdDto, tryPrice, now);
        }
        toPersist.setAsset(assetRegistry.upsert(MarketType.CRYPTO, toPersist.getId(), toPersist.getName()));
        cryptoRepository.save(toPersist);
        return toPersist;
    }

    public void replaceCandleHistory(String coinId, List<CryptoCandle> candles) {
        cryptoCandleRepository.deleteByCryptoId(coinId);
        cryptoCandleRepository.saveAll(candles);
    }

    public int upsertCandles(String coinId, Crypto crypto, List<CoinGeckoCandleDto> dtos) {
        CandleBatchUpsertTemplate.UpsertResult<CryptoCandle> upsertResult = CandleBatchUpsertTemplate.upsert(
                dtos,
                CoinGeckoCandleDto::candleDate,
                keys -> cryptoCandleRepository.findByCryptoIdAndCandleDateIn(coinId, keys),
                CryptoCandle::getCandleDate,
                cryptoMapper::updateCandleEntity,
                dto -> cryptoMapper.toCandleEntity(dto, crypto));
        if (!upsertResult.newEntities().isEmpty()) {
            cryptoCandleRepository.saveAll(upsertResult.newEntities());
        }
        return upsertResult.totalChanged();
    }
}
