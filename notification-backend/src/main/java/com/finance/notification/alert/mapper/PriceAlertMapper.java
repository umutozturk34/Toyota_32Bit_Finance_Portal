package com.finance.notification.alert.mapper;

import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;
import com.finance.notification.alert.dto.PriceAlertCreateRequest;
import com.finance.notification.alert.dto.PriceAlertResponse;
import com.finance.notification.alert.model.PriceAlert;
import com.finance.notification.core.dispatch.payload.PriceAlertPayload;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PriceAlertMapper {

    PriceAlertResponse toResponse(PriceAlert alert);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userSub", source = "userSub")
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "triggeredAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    PriceAlert toEntity(PriceAlertCreateRequest request, String userSub);

    @Mapping(target = "alertId", source = "alert.id")
    @Mapping(target = "marketType", source = "marketType")
    @Mapping(target = "assetCode", source = "alert.assetCode")
    @Mapping(target = "direction", source = "alert.direction")
    @Mapping(target = "threshold", source = "alert.threshold")
    @Mapping(target = "currentPrice", source = "snapshot.priceTry")
    @Mapping(target = "image", source = "snapshot.image")
    @Mapping(target = "assetName", source = "snapshot.name")
    PriceAlertPayload toFiredPayload(PriceAlert alert, AssetSnapshot snapshot, MarketType marketType);
}
