package com.finance.market.viop.dto.external;

import java.util.List;

public record ViopContractMetadataResponse<T>(
        String timestamp,
        List<T> value
) { }
