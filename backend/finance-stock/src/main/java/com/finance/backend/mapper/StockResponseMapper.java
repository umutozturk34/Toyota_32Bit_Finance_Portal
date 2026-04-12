package com.finance.backend.mapper;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.dto.response.StockResponse;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public abstract class StockResponseMapper {

    public abstract StockResponse toStockResponse(Stock stock);

    public abstract List<StockResponse> toStockResponses(List<Stock> stocks);

    public abstract CandleResponse toCandleResponse(StockCandle candle);

    public abstract List<CandleResponse> toStockCandleResponses(List<StockCandle> candles);

    @Mapping(target = "code", source = "symbol")
    @Mapping(target = "price", source = "currentPrice")
    @Mapping(target = "changeAmount", source = "priceChangeAmount")
    @Mapping(target = "changePercent", source = "priceChangePercent")
    @Mapping(target = "type", expression = "java(MarketType.STOCK)")
    @Mapping(target = "metadata", source = "stock", qualifiedByName = "stockMetadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Stock stock);

    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Stock> stocks);

    @Named("stockMetadata")
    protected Map<String, Object> buildStockMetadata(Stock stock) {
        Map<String, Object> metadata = new HashMap<>();
        if (stock.getStockSegment() != null) {
            metadata.put("stockSegment", stock.getStockSegment().name());
        }
        if (stock.getVolume() != null) {
            metadata.put("volume", stock.getVolume());
        }
        if (stock.getExchange() != null) {
            metadata.put("exchange", stock.getExchange());
        }
        if (stock.getOpenPrice() != null) {
            metadata.put("openPrice", stock.getOpenPrice());
        }
        if (stock.getDayHigh() != null) {
            metadata.put("dayHigh", stock.getDayHigh());
        }
        if (stock.getDayLow() != null) {
            metadata.put("dayLow", stock.getDayLow());
        }
        return metadata;
    }
}
