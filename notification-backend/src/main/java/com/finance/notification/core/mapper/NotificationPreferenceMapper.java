package com.finance.notification.core.mapper;

import com.finance.notification.core.dto.NotificationPreferenceResponse;
import com.finance.notification.core.dto.NotificationPreferenceUpdateRequest;
import com.finance.notification.core.model.NotificationPreference;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface NotificationPreferenceMapper {

    NotificationPreferenceResponse toResponse(NotificationPreference preference);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void apply(NotificationPreferenceUpdateRequest request, @MappingTarget NotificationPreference preference);
}
