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

/**
 * MapStruct mapper between price alert entities, request DTOs, snapshot-enriched responses and the
 * fired-alert notification payload.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PriceAlertMapper {

    @Mapping(target = "assetName", ignore = true)
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "currentPrice", ignore = true)
    @Mapping(target = "changeAmount", ignore = true)
    @Mapping(target = "changePercent", ignore = true)
    PriceAlertResponse toResponse(PriceAlert alert);

    @Mapping(target = "id", source = "alert.id")
    @Mapping(target = "marketType", source = "alert.marketType")
    @Mapping(target = "assetCode", source = "alert.assetCode")
    @Mapping(target = "assetName", source = "snapshot.name")
    @Mapping(target = "image", source = "snapshot.image")
    @Mapping(target = "currentPrice", source = "snapshot.priceTry")
    @Mapping(target = "changeAmount", source = "snapshot.changeAmount")
    @Mapping(target = "changePercent", source = "snapshot.changePercent")
    @Mapping(target = "direction", source = "alert.direction")
    @Mapping(target = "threshold", source = "alert.threshold")
    @Mapping(target = "currency", source = "alert.currency")
    @Mapping(target = "referencePrice", source = "alert.referencePrice")
    @Mapping(target = "active", source = "alert.active")
    @Mapping(target = "triggeredAt", source = "alert.triggeredAt")
    @Mapping(target = "createdAt", source = "alert.createdAt")
    PriceAlertResponse toResponse(PriceAlert alert, AssetSnapshot snapshot);

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
    @Mapping(target = "currency", source = "alert.currency")
    PriceAlertPayload toFiredPayload(PriceAlert alert, AssetSnapshot snapshot, MarketType marketType);
}
