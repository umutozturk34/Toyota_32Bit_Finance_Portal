package com.finance.news.dto.response;

/** An asset a news article mentions: the full market {@code code} (e.g. "AHGAZ.IS") and its {@code type} ("STOCK"). */
public record NewsAssetResponse(String code, String type) {}
