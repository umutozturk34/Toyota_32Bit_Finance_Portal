package com.finance.portfolio.mapper;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

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
