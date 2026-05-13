package com.finance.market.stock.mapper;
import com.finance.market.core.mapper.BaseMarketMapper;


import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.stock.dto.external.YahooStockQuoteDto;
import com.finance.common.model.StockSegment;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.model.StockCandle;
import org.mapstruct.*;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public abstract class StockMapper extends BaseMarketMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "stockSegment", ignore = true)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    public abstract Stock toEntity(YahooStockQuoteDto dto, LocalDateTime now);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "symbol", ignore = true)
    @Mapping(target = "stockSegment", ignore = true)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    public abstract void updateEntityFromDto(@MappingTarget Stock stock, YahooStockQuoteDto dto, LocalDateTime now);

    @AfterMapping
    void enrichStock(@MappingTarget Stock stock) {
        stock.applyChangePreferring(stock.getCurrentPrice(), stock.getPreviousClose(),
                stock.getChangeAmount(), stock.getChangePercent(), scale());
        stock.scaleFields(scale());
        if (stock.getStockSegment() == null && stock.getSymbol() != null) {
            stock.setStockSegment(StockSegment.EQUITY);
        }
    }

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "stockSymbol", expression = "java(stock.getSymbol())")
    @Mapping(source = "dto.volume", target = "volume")
    public abstract StockCandle toCandleEntity(YahooCandleDto dto, Stock stock);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    public abstract void updateCandleEntity(@MappingTarget StockCandle candle, YahooCandleDto dto);

    @AfterMapping
    void enrichStockCandle(@MappingTarget StockCandle candle) {
        candle.scaleFields(scale());
    }
}
