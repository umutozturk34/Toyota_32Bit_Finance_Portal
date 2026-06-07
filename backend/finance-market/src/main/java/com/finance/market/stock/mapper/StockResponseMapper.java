package com.finance.market.stock.mapper;

import com.finance.market.core.mapper.MarketMetadataBuilder;


import com.finance.market.core.dto.response.CandleResponse;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.shared.dto.response.StockMetadata;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.model.StockCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * MapStruct mapper exposing {@link Stock} and {@link StockCandle} entities as the generic market
 * API response DTOs. Each asset response is tagged with {@code MarketType.STOCK} and carries a
 * stock-specific {@link StockMetadata} block assembled by {@link #buildMetadata(Stock)}.
 */
@Mapper(componentModel = "spring")
public abstract class StockResponseMapper implements MarketMetadataBuilder<Stock, StockMetadata> {

    /** Maps a list of stock candles to generic candle responses, preserving order. */
    public abstract List<CandleResponse> toStockCandleResponses(List<StockCandle> candles);

    /**
     * Maps a {@link Stock} to the unified market-asset response: the symbol becomes the asset code,
     * the current price and day change are copied over, the type is fixed to {@code STOCK}, and the
     * stock-specific metadata is attached.
     */
    @Mapping(target = "code", source = "symbol")
    @Mapping(target = "price", source = "currentPrice")
    @Mapping(target = "changeAmount", source = "changeAmount")
    @Mapping(target = "changePercent", source = "changePercent")
    @Mapping(target = "type", expression = "java(MarketType.STOCK)")
    @Mapping(target = "metadata", source = "stock", qualifiedByName = "metadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Stock stock);

    /** Maps a list of stocks to unified market-asset responses, preserving order. */
    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Stock> stocks);

    /**
     * Builds the stock-specific metadata block (segment, volume, exchange, open/high/low) embedded
     * in each asset response. Used as the {@code "metadata"} qualifier for {@code toMarketAssetResponse}.
     */
    @Override
    @Named("metadata")
    public StockMetadata buildMetadata(Stock stock) {
        return new StockMetadata(
                stock.getStockSegment(),
                stock.getVolume(),
                stock.getExchange(),
                stock.getOpenPrice(),
                stock.getDayHigh(),
                stock.getDayLow()
        );
    }
}
