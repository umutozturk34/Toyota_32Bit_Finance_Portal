package com.finance.backend.mapper;

import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.dto.internal.TefasResponse;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class FundMapper {

    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");

    public List<TefasFundDto> toDto(List<TefasResponse.FundData> rawList) {
        return rawList.stream()
                .map(d -> new TefasFundDto(
                        d.fonKodu(),
                        d.fonUnvan(),
                        parseTimestamp(d.tarih()),
                        d.fiyat(),
                        d.borsaBultenFiyat(),
                        d.tedPaySayisi(),
                        d.kisiSayisi(),
                        d.portfolyoBuyukluk()))
                .toList();
    }

    private LocalDateTime parseTimestamp(String millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(millis)), ISTANBUL_ZONE);
    }

    public Fund toEntity(TefasFundDto dto, String fundType, LocalDateTime now) {
        return Fund.builder()
                .fundCode(dto.fundCode())
                .name(dto.name())
                .fundType(fundType)
                .price(scale6(dto.price()))
                .bulletinPrice("BYF".equals(fundType) ? scale4(dto.bulletinPrice()) : null)
                .shareCount(scale2(dto.shareCount()))
                .investorCount("YAT".equals(fundType) ? scale2(dto.investorCount()) : null)
                .portfolioSize(scale2(dto.portfolioSize()))
                .lastUpdated(now)
                .build();
    }

    public void updateEntity(Fund existing, TefasFundDto dto, String fundType, LocalDateTime now) {
        existing.setName(dto.name());
        existing.setFundType(fundType);
        existing.setPrice(scale6(dto.price()));
        existing.setBulletinPrice("BYF".equals(fundType) ? scale4(dto.bulletinPrice()) : null);
        existing.setShareCount(scale2(dto.shareCount()));
        existing.setInvestorCount("YAT".equals(fundType) ? scale2(dto.investorCount()) : null);
        existing.setPortfolioSize(scale2(dto.portfolioSize()));
        existing.setLastUpdated(now);
    }

    public FundCandle toCandleEntity(TefasFundDto dto, Fund fund, String fundType) {
        return FundCandle.builder()
                .fund(fund)
                .fundCode(fund.getFundCode())
                .fundType(fundType)
                .candleDate(dto.date())
                .price(scale6(dto.price()))
                .bulletinPrice("BYF".equals(fundType) ? scale4(dto.bulletinPrice()) : null)
                .shareCount(scale2(dto.shareCount()))
                .investorCount("YAT".equals(fundType) ? scale2(dto.investorCount()) : null)
                .portfolioSize(scale2(dto.portfolioSize()))
                .build();
    }

    public void updateCandleEntity(FundCandle existing, TefasFundDto dto) {
        existing.setPrice(scale6(dto.price()));
        existing.setBulletinPrice(scale4(dto.bulletinPrice()));
        existing.setShareCount(scale2(dto.shareCount()));
        existing.setInvestorCount(scale2(dto.investorCount()));
        existing.setPortfolioSize(scale2(dto.portfolioSize()));
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
