package com.finance.notification.core.mapper;

import com.finance.notification.core.dto.NotificationPreferenceResponse;
import com.finance.notification.core.model.NotificationPreference;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface NotificationPreferenceMapper {

    NotificationPreferenceResponse toResponse(NotificationPreference preference);
}
