package com.finance.market.fund.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TefasGenericResponse<T>(
        String errorCode,
        String errorMessage,
        List<T> resultList
) {}
