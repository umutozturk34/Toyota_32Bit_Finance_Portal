package com.finance.backend.mapper;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.dto.response.StockMetadata;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class StockResponseMapper implements MarketMetadataBuilder<Stock, StockMetadata> {

    public abstract List<CandleResponse> toStockCandleResponses(List<StockCandle> candles);

    @Mapping(target = "code", source = "symbol")
    @Mapping(target = "price", source = "currentPrice")
    @Mapping(target = "changeAmount", source = "changeAmount")
    @Mapping(target = "changePercent", source = "changePercent")
    @Mapping(target = "type", expression = "java(MarketType.STOCK)")
    @Mapping(target = "metadata", source = "stock", qualifiedByName = "metadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Stock stock);

    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Stock> stocks);

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
