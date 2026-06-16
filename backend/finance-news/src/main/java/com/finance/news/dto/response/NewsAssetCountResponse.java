package com.finance.news.dto.response;

/**
 * One row of the news "filter by asset" rail: an asset's full market {@code code} (e.g. "THYAO.IS"), its
 * {@code type} ("STOCK") and {@code count} — how many news articles mention it. Ordered most-mentioned first.
 */
public record NewsAssetCountResponse(String code, String type, long count) {}
