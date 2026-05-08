package com.finance.market.commodity.mapper;

import com.finance.common.dto.external.YahooCandleDto;
import com.finance.common.dto.external.YahooQuoteDto;
import com.finance.market.commodity.model.Commodity;
import com.finance.market.commodity.model.CommodityCandle;
import com.finance.market.commodity.model.CommoditySnapshotInput;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring")
public interface CommodityMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "commodity", source = "commodity")
    CommodityCandle toCandleEntity(YahooCandleDto dto, String commodityCode, Commodity commodity);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    void updateCandleEntity(@MappingTarget CommodityCandle existing, YahooCandleDto dto);

    default CommoditySnapshotInput toSnapshotInput(YahooQuoteDto quote,
                                                   YahooCandleDto todayTryCandle,
                                                   YahooCandleDto previousTryCandle) {
        return new CommoditySnapshotInput(
                todayTryCandle.close(),
                previousTryCandle != null ? previousTryCandle.close() : null,
                quote.regularMarketPrice(),
                quote.previousClose(),
                todayTryCandle.open(),
                todayTryCandle.high(),
                todayTryCandle.low(),
                quote.volume()
        );
    }
}
