package com.finance.user.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.user.dto.UserLayoutResponse;
import com.finance.user.model.UserLayout;
import org.mapstruct.Mapper;

import java.util.Map;

@Mapper(componentModel = "spring")
public abstract class UserLayoutMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public abstract UserLayoutResponse toResponse(UserLayout entity);

    protected Map<String, Object> map(JsonNode node) {
        if (node == null || node.isNull()) return Map.of();
        return OBJECT_MAPPER.convertValue(node, MAP_TYPE);
    }
}
