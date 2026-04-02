package com.finance.backend.mapper;

import com.finance.backend.dto.response.AssetSeriesPoint;
import com.finance.backend.dto.response.PerformancePoint;
import com.finance.backend.model.PortfolioAssetDailySnapshot;
import com.finance.backend.model.PortfolioDailySnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class PortfolioSnapshotMapper {

    @Mapping(target = "timestamp", source = "createdAt")
    @Mapping(target = "totalValueTry", expression = "java(snapshot.getTotalValueTry().subtract(snapshot.getCashBalanceTry()))")
    @Mapping(target = "pnlTry", source = "totalPnlTry")
    public abstract PerformancePoint toPerformancePoint(PortfolioDailySnapshot snapshot);

    public abstract List<PerformancePoint> toPerformancePoints(List<PortfolioDailySnapshot> snapshots);

    @Mapping(target = "timestamp", source = "createdAt")
    public abstract AssetSeriesPoint toAssetSeriesPoint(PortfolioAssetDailySnapshot snapshot);

    public abstract List<AssetSeriesPoint> toAssetSeriesPoints(List<PortfolioAssetDailySnapshot> snapshots);
}
