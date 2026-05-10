package com.finance.shared.util;

import com.finance.common.exception.BadRequestException;

public final class EnumParser {

    private EnumParser() {}

    public static <E extends Enum<E>> E parseOrBadRequest(Class<E> type, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("error.validation.fieldRequired", field);
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("error.validation.invalidField", field, raw);
        }
    }

    public static <E extends Enum<E>> E parseNullable(Class<E> type, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("error.validation.invalidField", field, raw);
        }
    }
}
