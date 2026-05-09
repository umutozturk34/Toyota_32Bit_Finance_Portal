package com.finance.market.core.mapper;

import com.finance.common.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseMarketMapper {

    @Autowired
    protected AppProperties appProperties;

    protected int scale() {
        return appProperties.getScale();
    }
}
