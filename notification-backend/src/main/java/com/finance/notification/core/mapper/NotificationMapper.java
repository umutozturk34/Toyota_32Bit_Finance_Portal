package com.finance.notification.core.mapper;

import com.finance.notification.core.dto.NotificationResponse;
import com.finance.notification.core.model.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/** MapStruct mapper from {@link Notification} entities to their client-facing response. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);
}
