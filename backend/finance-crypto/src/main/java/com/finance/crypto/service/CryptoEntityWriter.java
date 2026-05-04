package com.finance.crypto.service;
import com.finance.common.service.MarketEntityWriter;


import com.finance.crypto.dto.external.CoinGeckoCandleDto;
import com.finance.crypto.dto.external.CoinGeckoSnapshotDto;
import com.finance.crypto.mapper.CryptoMapper;
import com.finance.crypto.model.Crypto;
import com.finance.crypto.model.CryptoCandle;
import com.finance.crypto.repository.CryptoCandleRepository;
import com.finance.crypto.repository.CryptoRepository;
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
