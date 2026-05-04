package com.finance.notification.alert.mapper;

import com.finance.notification.alert.dto.PriceAlertCreateRequest;
import com.finance.notification.alert.dto.PriceAlertResponse;
import com.finance.notification.alert.model.PriceAlert;
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
}
