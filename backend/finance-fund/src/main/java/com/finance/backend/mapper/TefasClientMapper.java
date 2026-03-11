package com.finance.backend.mapper;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.dto.internal.TefasResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Mapper(componentModel = "spring")
public abstract class TefasClientMapper {

    @Autowired
    protected AppProperties appProperties;

    @Mapping(target = "fundCode", source = "fonKodu")
    @Mapping(target = "name", source = "fonUnvan")
    @Mapping(target = "date", expression = "java(parseDate(data.tarih()))")
    @Mapping(target = "price", source = "fiyat")
    @Mapping(target = "bulletinPrice", source = "borsaBultenFiyat")
    @Mapping(target = "shareCount", source = "tedPaySayisi")
    @Mapping(target = "investorCount", source = "kisiSayisi")
    @Mapping(target = "portfolioSize", source = "portfolyoBuyukluk")
    public abstract TefasFundDto toDto(TefasResponse.FundData data);

    protected LocalDateTime parseDate(String epochMillis) {
        ZoneId zone = ZoneId.of(appProperties.getTimezone());
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(epochMillis)), zone);
    }
}
