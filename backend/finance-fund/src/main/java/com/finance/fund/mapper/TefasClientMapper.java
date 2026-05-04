package com.finance.fund.mapper;

import com.finance.common.config.AppProperties;
import com.finance.fund.dto.external.TefasFundDto;
import com.finance.fund.dto.internal.TefasResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
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
    @Mapping(target = "portfolioSize", source = "portfoyBuyukluk")
    public abstract TefasFundDto toDto(TefasResponse.FundData data);

    protected LocalDateTime parseDate(String isoDate) {
        ZoneId zone = ZoneId.of(appProperties.getTimezone());
        return LocalDate.parse(isoDate).atStartOfDay(zone).toLocalDateTime();
    }
}
