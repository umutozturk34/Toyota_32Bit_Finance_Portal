package com.finance.fund.mapper;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

import com.finance.fund.dto.external.TefasFundDto;
import com.finance.fund.model.Fund;
import com.finance.fund.model.FundCandle;
import com.finance.fund.model.FundType;
import org.mapstruct.*;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public abstract class FundMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    @Mapping(target = "fundType", expression = "java(fundType)")
    public abstract Fund toEntity(TefasFundDto dto, FundType fundType, LocalDateTime now);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "fundCode", ignore = true)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    @Mapping(target = "fundType", expression = "java(fundType)")
    public abstract void updateEntity(@MappingTarget Fund fund, TefasFundDto dto, FundType fundType, LocalDateTime now);

    @AfterMapping
    void enrichFund(@MappingTarget Fund fund, FundType fundType) {
        fund.applyScaling(fundType);
    }

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
