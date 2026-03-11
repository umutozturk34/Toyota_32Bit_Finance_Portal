package com.finance.backend.mapper;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.ForexResponse;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ForexResponseMapper {

    public abstract ForexResponse toForexResponse(Forex forex);

    @Mapping(target = "volume", ignore = true)
    public abstract CandleResponse toCandleResponse(ForexCandle candle);

    public abstract List<CandleResponse> toForexCandleResponses(List<ForexCandle> candles);
}
