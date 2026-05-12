package com.finance.user.service;

import tools.jackson.databind.JsonNode;

@FunctionalInterface
public interface OverviewSaveSanitizer {
    JsonNode sanitize(JsonNode overview);
}
