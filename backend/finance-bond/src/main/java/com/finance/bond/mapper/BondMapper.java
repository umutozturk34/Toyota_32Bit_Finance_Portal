package com.finance.bond.mapper;
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

import com.finance.bond.dto.external.BondSnapshotDto;
import com.finance.bond.model.Bond;
import org.mapstruct.*;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public abstract class BondMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    @Mapping(target = "baseIndex", source = "dto.cleanPrice")
    @Mapping(target = "simpleYield", ignore = true)
    @Mapping(target = "bondType", ignore = true)
    @Mapping(target = "issuer", ignore = true)
    public abstract Bond toEntity(BondSnapshotDto dto, LocalDateTime now);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "seriesCode", ignore = true)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    @Mapping(target = "baseIndex", source = "dto.cleanPrice")
    @Mapping(target = "simpleYield", ignore = true)
    @Mapping(target = "bondType", ignore = true)
    @Mapping(target = "issuer", ignore = true)
    public abstract void updateEntity(@MappingTarget Bond bond, BondSnapshotDto dto, LocalDateTime now);
}
