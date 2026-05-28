package com.finance.shared.dto.response;

/** Aggregation result row pairing a group key (e.g. asset type) with its element count. */
public record GroupCount(String type, long count) {
}
