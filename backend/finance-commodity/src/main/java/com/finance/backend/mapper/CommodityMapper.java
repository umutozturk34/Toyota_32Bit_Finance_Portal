package com.finance.backend.mapper;

import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.CommoditySnapshotInput;
import com.finance.backend.util.SyntheticPriceCalculator;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface CommodityMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "commodity", source = "commodity")
    CommodityCandle toCandleEntity(YahooCandleDto dto, String commodityCode, Commodity commodity);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    void updateCandleEntity(@MappingTarget CommodityCandle existing, YahooCandleDto dto);

    default CommoditySnapshotInput toSnapshotInput(YahooQuoteDto quote, BigDecimal usdTryRate, int scale) {
        if (quote == null) return null;
        BigDecimal usdPrice = quote.regularMarketPrice();
        BigDecimal usdPreviousClose = quote.previousClose();
        BigDecimal tryPrice = SyntheticPriceCalculator.calculateSyntheticPrice(usdPrice, usdTryRate, false, scale);
        BigDecimal tryPreviousClose = SyntheticPriceCalculator.calculateSyntheticPrice(usdPreviousClose, usdTryRate, false, scale);
        return new CommoditySnapshotInput(tryPrice, tryPreviousClose, usdPrice, usdPreviousClose);
    }
}
