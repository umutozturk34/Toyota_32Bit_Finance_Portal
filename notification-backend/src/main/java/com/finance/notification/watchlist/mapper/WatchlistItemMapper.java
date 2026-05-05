package com.finance.notification.watchlist.mapper;

import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.notification.core.dispatch.payload.WatchlistDeltaPayload;
import com.finance.notification.watchlist.dto.WatchlistItemCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistItemResponse;
import com.finance.notification.watchlist.model.WatchlistItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.math.BigDecimal;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface WatchlistItemMapper {

    @Mapping(target = "assetName", ignore = true)
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "currentPrice", ignore = true)
    WatchlistItemResponse toResponse(WatchlistItem item);

    @Mapping(target = "id", source = "item.id")
    @Mapping(target = "marketType", source = "item.marketType")
    @Mapping(target = "assetCode", source = "item.assetCode")
    @Mapping(target = "assetName", source = "snapshot.name")
    @Mapping(target = "image", source = "snapshot.image")
    @Mapping(target = "currentPrice", source = "snapshot.priceTry")
    @Mapping(target = "note", source = "item.note")
    @Mapping(target = "deltaThreshold", source = "item.deltaThreshold")
    @Mapping(target = "lastSeenPrice", source = "item.lastSeenPrice")
    @Mapping(target = "lastSeenAt", source = "item.lastSeenAt")
    @Mapping(target = "createdAt", source = "item.createdAt")
    WatchlistItemResponse toResponse(WatchlistItem item, AssetSnapshot snapshot);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "watchlistId", ignore = true)
    @Mapping(target = "userSub", source = "userSub")
    @Mapping(target = "lastSeenPrice", ignore = true)
    @Mapping(target = "lastSeenAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    WatchlistItem toEntity(WatchlistItemCreateRequest request, String userSub);

    @Mapping(target = "watchlistItemId", source = "item.id")
    @Mapping(target = "assetCode", source = "item.assetCode")
    @Mapping(target = "assetName", source = "snapshot.name")
    @Mapping(target = "image", source = "snapshot.image")
    @Mapping(target = "lastSeenPrice", source = "item.lastSeenPrice")
    @Mapping(target = "currentPrice", source = "currentPrice")
    @Mapping(target = "deltaPercent", source = "deltaPercent")
    WatchlistDeltaPayload.DeltaItem toFiredItem(WatchlistItem item, AssetSnapshot snapshot,
                                                BigDecimal currentPrice, BigDecimal deltaPercent);
}
