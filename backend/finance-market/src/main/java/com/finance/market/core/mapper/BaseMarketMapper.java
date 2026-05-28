package com.finance.market.core.mapper;

import com.finance.common.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;

/** Base for market mappers needing the configured price {@link #scale()}. */
public abstract class BaseMarketMapper {

    @Autowired
    protected AppProperties appProperties;

    /** Configured decimal scale for monetary values. */
    protected int scale() {
        return appProperties.getScale();
    }
}
