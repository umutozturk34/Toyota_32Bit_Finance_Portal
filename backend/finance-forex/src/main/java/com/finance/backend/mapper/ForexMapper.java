package com.finance.backend.mapper;

import com.finance.backend.dto.external.TcmbRateDto;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public abstract class ForexMapper extends BaseMarketMapper {

    public String toCurrencyPairCode(String rawCurrencyCode) {
        return rawCurrencyCode + "TRY";
    }

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "currencyCode", expression = "java(dto.currencyCode() + \"TRY\")")
    public abstract Forex toEntity(TcmbRateDto dto);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "currencyCode", ignore = true)
    public abstract void updateEntity(@MappingTarget Forex existing, TcmbRateDto dto);

    @AfterMapping
    void enrichFromTcmb(@MappingTarget Forex forex, TcmbRateDto dto) {
        forex.applyTcmbData(dto, scale());
    }

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "forex", source = "forex")
    public abstract ForexCandle toCandleEntity(YahooCandleDto dto, String currencyCode, Forex forex);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    public abstract void updateCandleEntity(@MappingTarget ForexCandle existing, YahooCandleDto dto);

    @AfterMapping
    void enrichForexCandle(@MappingTarget ForexCandle candle) {
        candle.scaleAndNormalizeOhlc(scale());
        if (candle.getCandleDate() != null) {
            candle.setCandleDate(candle.getCandleDate().toLocalDate().atStartOfDay());
        }
    }
}
