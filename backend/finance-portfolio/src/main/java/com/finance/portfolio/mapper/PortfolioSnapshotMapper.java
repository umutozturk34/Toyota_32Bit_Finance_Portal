package com.finance.portfolio.mapper;
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

import com.finance.portfolio.dto.response.AssetSeriesPoint;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class PortfolioSnapshotMapper {

    @Mapping(target = "timestamp", source = "createdAt")
    public abstract AssetSeriesPoint toAssetSeriesPoint(PortfolioAssetDailySnapshot snapshot);

    public abstract List<AssetSeriesPoint> toAssetSeriesPoints(List<PortfolioAssetDailySnapshot> snapshots);
}
