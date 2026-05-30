package com.finance.shared.util;

import com.finance.common.exception.BadRequestException;

/**
 * Parses request strings into enum constants, translating absent/invalid input into localized
 * {@link BadRequestException}s so controllers get HTTP 400 instead of raw {@link IllegalArgumentException}s.
 */
public final class EnumParser {

    private EnumParser() {}

    /** Parses a required enum value; throws {@link BadRequestException} when blank or unrecognized. */
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

    /** Parses an optional enum value; returns null when blank, but still rejects unrecognized values. */
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
