package com.finance.backend.mapper;

import com.finance.backend.dto.response.AssetSeriesPoint;
import com.finance.backend.model.PortfolioAssetDailySnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class PortfolioSnapshotMapper {

    @Mapping(target = "timestamp", source = "createdAt")
    public abstract AssetSeriesPoint toAssetSeriesPoint(PortfolioAssetDailySnapshot snapshot);

    public abstract List<AssetSeriesPoint> toAssetSeriesPoints(List<PortfolioAssetDailySnapshot> snapshots);
}
