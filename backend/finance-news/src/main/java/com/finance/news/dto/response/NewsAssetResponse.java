package com.finance.news.dto.response;

/**
 * An asset a news article mentions: the full market {@code code} (e.g. "AHGAZ.IS"), its {@code type} ("STOCK") and
 * {@code mentionCount} — how many times the article references it (prominence hint shown in the UI).
 */
public record NewsAssetResponse(String code, String type, int mentionCount) {}
