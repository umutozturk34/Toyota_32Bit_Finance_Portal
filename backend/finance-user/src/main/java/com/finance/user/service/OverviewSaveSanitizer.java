package com.finance.user.service;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface OverviewSaveSanitizer {
    JsonNode sanitize(JsonNode overview);
}
