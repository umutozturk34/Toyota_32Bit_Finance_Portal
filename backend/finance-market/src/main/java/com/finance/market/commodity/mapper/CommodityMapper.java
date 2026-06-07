package com.finance.market.commodity.mapper;

import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.core.dto.external.YahooQuoteDto;
import com.finance.market.commodity.model.Commodity;
import com.finance.market.commodity.model.CommodityCandle;
import com.finance.market.commodity.model.CommoditySnapshotInput;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper between Yahoo Finance DTOs and commodity persistence/domain objects.
 * It builds and updates {@link CommodityCandle} history rows and assembles the
 * {@link CommoditySnapshotInput} that the snapshot pipeline consumes.
 */
@Mapper(componentModel = "spring")
public interface CommodityMapper {

    /**
     * Builds a new candle entity from a Yahoo OHLC bar, linking it to its owning commodity.
     * The generated {@code id} is left null for the persistence layer to assign.
     *
     * @param dto           Yahoo candle bar
     * @param commodityCode commodity series code the candle belongs to
     * @param commodity     owning commodity entity association
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "commodity", source = "commodity")
    CommodityCandle toCandleEntity(YahooCandleDto dto, String commodityCode, Commodity commodity);

    /** Updates an existing candle in place with the values from a newer Yahoo bar. */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    void updateCandleEntity(@MappingTarget CommodityCandle existing, YahooCandleDto dto);

    /**
     * Combines a live Yahoo quote with today's and the previous day's TRY-denominated candles
     * into a {@link CommoditySnapshotInput}. The TRY candles supply the converted current and
     * previous prices (previous price is null when no prior candle exists), while open/high/low
     * also come from today's TRY candle and volume/change% come from the quote.
     */
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
                quote.volume(),
                quote.regularMarketChangePercent()
        );
    }
}
