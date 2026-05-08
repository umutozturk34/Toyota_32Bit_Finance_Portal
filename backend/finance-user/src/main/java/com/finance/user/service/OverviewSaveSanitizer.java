package com.finance.user.service;

import java.util.Map;

@FunctionalInterface
public interface OverviewSaveSanitizer {
    Map<String, Object> sanitize(Map<String, Object> overview);
}
