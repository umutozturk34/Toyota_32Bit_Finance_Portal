package com.finance.market.fund.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Generic envelope for TEFAS list responses, parameterized over the row type {@code T}.
 * Wraps an optional error code/message with the returned {@code resultList}; an absent
 * or empty list paired with a populated {@code errorMessage} signals an upstream failure.
 *
 * @param <T> the element type of each row in {@code resultList}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TefasGenericResponse<T>(
        String errorCode,
        String errorMessage,
        List<T> resultList
) {}
