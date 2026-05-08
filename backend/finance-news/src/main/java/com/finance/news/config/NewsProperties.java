package com.finance.news.config;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.event.*;
import com.finance.common.repository.*;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.news")
public class NewsProperties {

    private int maxArticlesPerSource = 50;
    private int cacheTtlHours = 24;
    private int defaultCategoryLimit = 20;
    private Map<String, Integer> categoryLimits = new HashMap<>();
    private Mapping mapping = new Mapping();

    @Getter
    @Setter
    public static class Mapping {
        private int richHtmlMinLength = 150;
        private int shortDescriptionThreshold = 80;
    }
}
