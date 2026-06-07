package com.finance.market.bond.mapper;

import com.finance.market.bond.dto.response.BondRateResponse;
import com.finance.market.bond.dto.response.BondResponse;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper that projects bond persistence entities onto their outbound API responses:
 * {@link Bond} to {@link BondResponse} and {@link BondRateHistory} to {@link BondRateResponse}.
 */
@Mapper(componentModel = "spring")
public abstract class BondResponseMapper {

    /** Maps a single bond entity to its API response representation. */
    public abstract BondResponse toBondResponse(Bond bond);

    /** Maps a list of bond entities to API responses, preserving order. */
    public abstract List<BondResponse> toBondResponses(List<Bond> bonds);

    /**
     * Maps one historical coupon record to a rate-point response, renaming
     * {@code rateDate} to {@code date} and {@code couponRate} to {@code rate}.
     */
    @Mapping(target = "date", source = "rateDate")
    @Mapping(target = "rate", source = "couponRate")
    public abstract BondRateResponse toRateResponse(BondRateHistory history);

    /** Maps a coupon-rate history series to response points, preserving order. */
    public abstract List<BondRateResponse> toRateResponses(List<BondRateHistory> histories);
}
