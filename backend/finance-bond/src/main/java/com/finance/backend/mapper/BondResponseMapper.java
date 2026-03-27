package com.finance.backend.mapper;

import com.finance.backend.dto.response.BondRateResponse;
import com.finance.backend.dto.response.BondResponse;
import com.finance.backend.model.Bond;
import com.finance.backend.model.BondRateHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class BondResponseMapper {

    public abstract BondResponse toBondResponse(Bond bond);

    public abstract List<BondResponse> toBondResponses(List<Bond> bonds);

    @Mapping(target = "date", source = "rateDate")
    @Mapping(target = "rate", source = "couponRate")
    public abstract BondRateResponse toRateResponse(BondRateHistory history);

    public abstract List<BondRateResponse> toRateResponses(List<BondRateHistory> histories);
}
