package com.finance.backend.mapper;

import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public abstract class CommodityMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "commodity", expression = "java(commodity)")
    @Mapping(target = "commodityCode", expression = "java(commodityCode)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract CommodityCandle toCandleEntity(YahooCandleDto dto, String commodityCode, Commodity commodity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "commodity", ignore = true)
    @Mapping(target = "commodityCode", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract void updateCandleEntity(YahooCandleDto dto, @MappingTarget CommodityCandle entity);
}
