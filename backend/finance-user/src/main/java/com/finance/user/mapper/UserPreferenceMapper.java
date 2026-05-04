package com.finance.user.mapper;

import com.finance.user.dto.UserPreferenceResponse;
import com.finance.common.event.UserPreferencesUpdatedEvent;
import com.finance.user.model.UserPreference;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.OffsetDateTime;
import java.util.UUID;

@Mapper(componentModel = "spring", imports = {UUID.class, OffsetDateTime.class})
public interface UserPreferenceMapper {

    UserPreferenceResponse toResponse(UserPreference entity);

    @Mapping(target = "eventId", expression = "java(UUID.randomUUID().toString())")
    @Mapping(target = "occurredAt", expression = "java(OffsetDateTime.now())")
    @Mapping(target = "theme", source = "theme", qualifiedByName = "themeName")
    @Mapping(target = "reportFrequency", source = "reportFrequency", qualifiedByName = "reportFrequencyName")
    UserPreferencesUpdatedEvent toUpdatedEvent(UserPreference entity);

    @Named("themeName")
    static String themeName(com.finance.user.dto.enums.ThemePreference theme) {
        return theme != null ? theme.name() : null;
    }

    @Named("reportFrequencyName")
    static String reportFrequencyName(com.finance.user.dto.enums.ReportFrequency frequency) {
        return frequency != null ? frequency.name() : null;
    }
}
