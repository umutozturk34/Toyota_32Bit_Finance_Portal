package com.finance.portfolio.mapper;

import com.finance.portfolio.dto.response.AssetSeriesPoint;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** MapStruct mapper converting stored per-asset snapshots into chart {@link AssetSeriesPoint}s; trade events are attached later by the service. */
@Mapper(componentModel = "spring")
public abstract class PortfolioSnapshotMapper {

    @Mapping(target = "timestamp", source = "createdAt")
    @Mapping(target = "events", ignore = true)
    @Mapping(target = "withEvents", ignore = true)
    public abstract AssetSeriesPoint toAssetSeriesPoint(PortfolioAssetDailySnapshot snapshot);

    public abstract List<AssetSeriesPoint> toAssetSeriesPoints(List<PortfolioAssetDailySnapshot> snapshots);
}
