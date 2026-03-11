package com.finance.backend.mapper;

import com.finance.backend.dto.response.FundCandleResponse;
import com.finance.backend.dto.response.FundResponse;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class FundResponseMapper {

    public abstract FundResponse toFundResponse(Fund fund);

    public abstract List<FundResponse> toFundResponses(List<Fund> funds);

    public abstract FundCandleResponse toFundCandleResponse(FundCandle candle);

    public abstract List<FundCandleResponse> toFundCandleResponses(List<FundCandle> candles);
}
