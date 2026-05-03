package com.finance.backend.mapper;

import com.finance.backend.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseMarketMapper {

    @Autowired
    protected AppProperties appProperties;

    protected int scale() {
        return appProperties.getScale();
    }
}
