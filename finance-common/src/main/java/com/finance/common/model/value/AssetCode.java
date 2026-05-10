package com.finance.common.model.value;

import com.finance.common.exception.BadRequestException;

public record AssetCode(String value) {

    public AssetCode {
        if (value == null) {
            throw new BadRequestException("error.assetCode.null");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("error.assetCode.blank");
        }
        value = trimmed.toUpperCase();
    }

    public static AssetCode of(String raw) {
        return new AssetCode(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
