package com.finance.backend.mapper;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooStockQuoteDto;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public abstract class StockMapper {

    @Autowired
    protected AppProperties appProperties;

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "priceChangeAmount", ignore = true)
    @Mapping(target = "priceChangePercent", ignore = true)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    public abstract Stock toEntity(YahooStockQuoteDto dto, LocalDateTime now);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "symbol", ignore = true)
    @Mapping(target = "priceChangeAmount", ignore = true)
    @Mapping(target = "priceChangePercent", ignore = true)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    public abstract void updateEntityFromDto(@MappingTarget Stock stock, YahooStockQuoteDto dto, LocalDateTime now);

    @AfterMapping
    void enrichStock(@MappingTarget Stock stock) {
        stock.scaleAndComputeChange(appProperties.getScale());
    }

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "stockSymbol", expression = "java(stock.getSymbol())")
    @Mapping(source = "dto.volume", target = "volume")
    public abstract StockCandle toCandleEntity(YahooCandleDto dto, Stock stock);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    public abstract void updateCandleEntity(@MappingTarget StockCandle candle, YahooCandleDto dto);

    @AfterMapping
    void enrichStockCandle(@MappingTarget StockCandle candle) {
        candle.scaleOhlc(appProperties.getScale());
    }
}
