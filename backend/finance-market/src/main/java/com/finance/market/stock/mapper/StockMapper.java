package com.finance.market.stock.mapper;
import com.finance.market.core.mapper.BaseMarketMapper;


import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.stock.dto.external.YahooStockQuoteDto;
import com.finance.common.model.StockSegment;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.model.StockCandle;
import org.mapstruct.*;

import java.time.LocalDateTime;

/**
 * MapStruct mapper converting Yahoo Finance quote/candle DTOs into {@link Stock} and
 * {@link StockCandle} entities. After each mapping it normalizes derived fields: prices are
 * scaled to the configured precision (via {@link BaseMarketMapper#scale()}), the day's change is
 * recomputed from the available figures, and an absent segment defaults to {@code EQUITY}.
 */
@Mapper(componentModel = "spring")
public abstract class StockMapper extends BaseMarketMapper {

    /**
     * Builds a new {@link Stock} from a Yahoo quote, stamping {@code lastUpdated} with the supplied
     * instant and leaving the segment unset for {@link #enrichStock} to default.
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "stockSegment", ignore = true)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    public abstract Stock toEntity(YahooStockQuoteDto dto, LocalDateTime now);

    /**
     * Refreshes an existing {@link Stock} in place from a newer quote, stamping {@code lastUpdated}
     * with the supplied instant while preserving the identifying symbol and the existing segment.
     */
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

    /**
     * Builds a {@link StockCandle} from a Yahoo OHLC bar, back-linking it to the owning stock's
     * symbol so the candle is self-describing without a navigable entity reference.
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "stockSymbol", expression = "java(stock.getSymbol())")
    @Mapping(source = "dto.volume", target = "volume")
    public abstract StockCandle toCandleEntity(YahooCandleDto dto, Stock stock);

    /** Refreshes an existing candle's OHLC/volume values in place from a re-fetched Yahoo bar. */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    public abstract void updateCandleEntity(@MappingTarget StockCandle candle, YahooCandleDto dto);

    @AfterMapping
    void enrichStockCandle(@MappingTarget StockCandle candle) {
        candle.scaleFields(scale());
    }
}
