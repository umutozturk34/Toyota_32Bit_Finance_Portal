package com.finance.backend.model.value;

public record AssetCode(String value) {

    public AssetCode {
        if (value == null) {
            throw new IllegalArgumentException("AssetCode value cannot be null");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("AssetCode value cannot be blank");
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
