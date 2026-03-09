package com.finance.backend.mapper;

import com.finance.backend.dto.external.TefasResponse.FundData;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class FundMapper {

    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");

    public Fund toEntity(FundData dto, String fundType, LocalDateTime now) {
        return Fund.builder()
                .fundCode(dto.fonKodu())
                .name(dto.fonUnvan())
                .fundType(fundType)
                .price(scale6(dto.fiyat()))
                .bulletinPrice("BYF".equals(fundType) ? scale4(dto.borsaBultenFiyat()) : null)
                .shareCount(scale2(dto.tedPaySayisi()))
                .investorCount("YAT".equals(fundType) ? scale2(dto.kisiSayisi()) : null)
                .portfolioSize(scale2(dto.portfolyoBuyukluk()))
                .lastUpdated(now)
                .build();
    }

    public void updateEntity(Fund existing, FundData dto, String fundType, LocalDateTime now) {
        existing.setName(dto.fonUnvan());
        existing.setFundType(fundType);
        existing.setPrice(scale6(dto.fiyat()));
        existing.setBulletinPrice("BYF".equals(fundType) ? scale4(dto.borsaBultenFiyat()) : null);
        existing.setShareCount(scale2(dto.tedPaySayisi()));
        existing.setInvestorCount("YAT".equals(fundType) ? scale2(dto.kisiSayisi()) : null);
        existing.setPortfolioSize(scale2(dto.portfolyoBuyukluk()));
        existing.setLastUpdated(now);
    }

    public FundCandle toCandleEntity(FundData dto, Fund fund, String fundType) {
        return FundCandle.builder()
                .fund(fund)
                .fundCode(fund.getFundCode())
                .fundType(fundType)
                .candleDate(parseTimestamp(dto.tarih()))
                .price(scale6(dto.fiyat()))
                .bulletinPrice("BYF".equals(fundType) ? scale4(dto.borsaBultenFiyat()) : null)
                .shareCount(scale2(dto.tedPaySayisi()))
                .investorCount("YAT".equals(fundType) ? scale2(dto.kisiSayisi()) : null)
                .portfolioSize(scale2(dto.portfolyoBuyukluk()))
                .build();
    }

    public void updateCandleEntity(FundCandle existing, FundData dto) {
        existing.setPrice(scale6(dto.fiyat()));
        existing.setBulletinPrice(scale4(dto.borsaBultenFiyat()));
        existing.setShareCount(scale2(dto.tedPaySayisi()));
        existing.setInvestorCount(scale2(dto.kisiSayisi()));
        existing.setPortfolioSize(scale2(dto.portfolyoBuyukluk()));
    }

    public LocalDateTime parseTimestamp(String millis) {
        long ms = Long.parseLong(millis);
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ISTANBUL_ZONE);
    }

    private BigDecimal scale6(BigDecimal value) {
        return value != null ? value.setScale(6, RoundingMode.HALF_UP) : null;
    }

    private BigDecimal scale4(BigDecimal value) {
        return value != null ? value.setScale(4, RoundingMode.HALF_UP) : null;
    }

    private BigDecimal scale2(BigDecimal value) {
        return value != null ? value.setScale(2, RoundingMode.HALF_UP) : null;
    }
}
