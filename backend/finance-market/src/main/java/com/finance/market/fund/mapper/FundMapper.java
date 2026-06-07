package com.finance.market.fund.mapper;

import com.finance.market.fund.dto.external.TefasFundDto;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundCandle;
import com.finance.market.fund.model.FundType;
import org.mapstruct.*;

import java.time.LocalDateTime;

/**
 * MapStruct mapper between TEFAS fund DTOs and the fund persistence model. It builds and updates
 * both {@link Fund} master records and {@link FundCandle} history rows, tagging each with the
 * resolved {@link FundType}. After mapping, scaling hooks normalize price-related fields to their
 * canonical scale ({@link Fund#applyScaling}/{@link FundCandle#applyScaling}/{@code scaleFields}).
 */
@Mapper(componentModel = "spring")
public abstract class FundMapper {

    /**
     * Builds a new {@link Fund} master record from a TEFAS DTO.
     *
     * @param dto      TEFAS fund payload
     * @param fundType resolved fund type, stamped onto the entity and used for scaling
     * @param now      timestamp recorded as the entity's last-updated marker
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    @Mapping(target = "fundType", expression = "java(fundType)")
    public abstract Fund toEntity(TefasFundDto dto, FundType fundType, LocalDateTime now);

    /**
     * Refreshes an existing {@link Fund} in place from a newer TEFAS DTO. The {@code fundCode}
     * identity field is preserved.
     *
     * @param fund     managed entity to update
     * @param dto      TEFAS fund payload carrying new values
     * @param fundType resolved fund type, re-stamped and used for scaling
     * @param now      timestamp recorded as the entity's last-updated marker
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "fundCode", ignore = true)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    @Mapping(target = "fundType", expression = "java(fundType)")
    public abstract void updateEntity(@MappingTarget Fund fund, TefasFundDto dto, FundType fundType, LocalDateTime now);

    @AfterMapping
    void enrichFund(@MappingTarget Fund fund, FundType fundType) {
        fund.applyScaling(fundType);
    }

    /**
     * Builds a new {@link FundCandle} history row from a TEFAS DTO, inheriting the {@code fundCode}
     * from the owning fund and mapping the DTO's date and price/volume metrics onto the candle.
     *
     * @param dto      TEFAS fund payload for a single date
     * @param fund     owning fund supplying the candle's fund code
     * @param fundType resolved fund type, stamped onto the candle and used for scaling
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "fundCode", expression = "java(fund.getFundCode())")
    @Mapping(target = "candleDate", source = "dto.date")
    @Mapping(target = "fundType", expression = "java(fundType)")
    @Mapping(source = "dto.price", target = "price")
    @Mapping(source = "dto.bulletinPrice", target = "bulletinPrice")
    @Mapping(source = "dto.shareCount", target = "shareCount")
    @Mapping(source = "dto.investorCount", target = "investorCount")
    @Mapping(source = "dto.portfolioSize", target = "portfolioSize")
    public abstract FundCandle toCandleEntity(TefasFundDto dto, Fund fund, FundType fundType);

    /** Updates an existing fund candle in place with values from a newer TEFAS DTO. */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    public abstract void updateCandleEntity(@MappingTarget FundCandle candle, TefasFundDto dto);

    @AfterMapping
    void enrichNewFundCandle(@MappingTarget FundCandle candle, FundType fundType) {
        candle.applyScaling(fundType);
    }

    @AfterMapping
    void scaleExistingFundCandle(@MappingTarget FundCandle candle) {
        candle.scaleFields(4);
    }
}
