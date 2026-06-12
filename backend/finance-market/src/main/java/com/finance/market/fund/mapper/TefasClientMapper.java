package com.finance.market.fund.mapper;

import com.finance.common.config.AppProperties;
import com.finance.market.fund.dto.external.TefasFundDto;
import com.finance.market.fund.dto.internal.TefasResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * MapStruct mapper translating a single TEFAS fund record from the external API shape
 * ({@link TefasResponse.FundData}, Turkish field names) into the internal {@link TefasFundDto}.
 * The fund's trade date is reinterpreted at start-of-day in the application timezone.
 */
@Mapper(componentModel = "spring")
public abstract class TefasClientMapper {

    private AppProperties appProperties;

    @Autowired
    protected void setAppProperties(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Maps one external TEFAS fund record to the internal DTO, renaming the Turkish source
     * fields and converting the textual ISO date via {@link #parseDate(String)}.
     */
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
