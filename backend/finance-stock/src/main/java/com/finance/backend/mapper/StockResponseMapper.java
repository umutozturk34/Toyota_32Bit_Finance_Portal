package com.finance.backend.mapper;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.StockResponse;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class StockResponseMapper {

    public abstract StockResponse toStockResponse(Stock stock);

    public abstract List<StockResponse> toStockResponses(List<Stock> stocks);

    public abstract CandleResponse toCandleResponse(StockCandle candle);

    public abstract List<CandleResponse> toStockCandleResponses(List<StockCandle> candles);
}
