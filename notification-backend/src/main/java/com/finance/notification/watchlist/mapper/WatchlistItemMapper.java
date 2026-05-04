package com.finance.notification.watchlist.mapper;

import com.finance.notification.watchlist.dto.WatchlistItemCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistItemResponse;
import com.finance.notification.watchlist.model.WatchlistItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface WatchlistItemMapper {

    WatchlistItemResponse toResponse(WatchlistItem item);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "watchlistId", ignore = true)
    @Mapping(target = "userSub", source = "userSub")
    @Mapping(target = "lastSeenPrice", ignore = true)
    @Mapping(target = "lastSeenAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    WatchlistItem toEntity(WatchlistItemCreateRequest request, String userSub);
}
