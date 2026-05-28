package com.finance.market.viop.dto.external;

import java.util.List;

/** Generic İş Yatırım metadata envelope wrapping a {@code value} list of future/option DTOs. */
public record ViopContractMetadataResponse<T>(
        String timestamp,
        List<T> value
) { }
