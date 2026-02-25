package com.finance.backend.mapper;
import com.finance.backend.dto.external.CoinGeckoCandleDto;
import com.finance.backend.dto.external.CoinGeckoMarketDto;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
@Component
public class CryptoMapper {
    private static final int SCALE = 4;
    public Crypto toEntity(CoinGeckoMarketDto usdDto, BigDecimal tryPrice, LocalDateTime now) {
        return Crypto.builder()
                .id(usdDto.id())
                .symbol(usdDto.symbol())
                .name(usdDto.name())
                .image(usdDto.image())
                .currentPrice(scale(usdDto.currentPrice()))
                .currentPriceTry(tryPrice != null ? scale(tryPrice) : null)
                .changeAmount(scale(usdDto.priceChange24h()))
                .changePercent(scale(usdDto.priceChangePercentage24h()))
                .marketCap(scale(usdDto.marketCap()))
                .totalVolume(scale(usdDto.totalVolume()))
                .exchange("CoinGecko")
                .currency("USD")
                .lastUpdated(now)
                .build();
    }
    public void updateEntityFromDto(Crypto existing, CoinGeckoMarketDto usdDto,
                                    BigDecimal tryPrice, LocalDateTime now) {
        existing.setSymbol(usdDto.symbol());
        existing.setName(usdDto.name());
        existing.setImage(usdDto.image());
        existing.setCurrentPrice(scale(usdDto.currentPrice()));
        existing.setCurrentPriceTry(tryPrice != null ? scale(tryPrice) : null);
        existing.setChangeAmount(scale(usdDto.priceChange24h()));
        existing.setChangePercent(scale(usdDto.priceChangePercentage24h()));
        existing.setMarketCap(scale(usdDto.marketCap()));
        existing.setTotalVolume(scale(usdDto.totalVolume()));
        existing.setLastUpdated(now);
    }
    public CryptoCandle toCandleEntity(CoinGeckoCandleDto dto, Crypto crypto) {
        return CryptoCandle.builder()
                .crypto(crypto)
                .cryptoId(dto.coinId())
                .candleDate(dto.candleDate().truncatedTo(ChronoUnit.DAYS))
                .open(scale(dto.open()))
                .high(scale(dto.high()))
                .low(scale(dto.low()))
                .close(scale(dto.close()))
                .volume(dto.volume())
                .build();
    }
    public void updateCandleEntity(CryptoCandle existing, CoinGeckoCandleDto dto) {
        existing.setOpen(scale(dto.open()));
        existing.setHigh(scale(dto.high()));
        existing.setLow(scale(dto.low()));
        existing.setClose(scale(dto.close()));
        existing.setVolume(dto.volume());
    }
    private BigDecimal scale(BigDecimal value) {
        return value != null ? value.setScale(SCALE, RoundingMode.HALF_UP) : null;
    }
}
