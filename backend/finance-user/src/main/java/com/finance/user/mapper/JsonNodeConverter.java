package com.finance.user.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JsonNodeConverter {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull()) return new LinkedHashMap<>();
        return objectMapper.convertValue(node, MAP_TYPE);
    }

    public List<Map<String, Object>> toMapList(JsonNode node) {
        if (node == null || node.isNull()) return new ArrayList<>();
        return objectMapper.convertValue(node, LIST_TYPE);
    }
}
