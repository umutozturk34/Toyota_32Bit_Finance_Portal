package com.finance.market.bond.mapper;
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
import com.finance.market.core.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.market.core.scheduler.*;
import com.finance.common.event.*;
import com.finance.market.core.mapper.*;
import com.finance.common.repository.*;
import com.finance.market.core.client.*;

import com.finance.market.bond.dto.response.BondRateResponse;
import com.finance.market.bond.dto.response.BondResponse;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
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
