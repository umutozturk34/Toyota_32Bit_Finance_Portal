package com.finance.user.service;

import tools.jackson.databind.JsonNode;

/** Strategy for normalizing/validating a dashboard overview payload before it is persisted. */
@FunctionalInterface
public interface OverviewSaveSanitizer {
    JsonNode sanitize(JsonNode overview);
}
