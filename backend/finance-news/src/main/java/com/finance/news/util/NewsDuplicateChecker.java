package com.finance.news.util;
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
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

import com.finance.news.dto.internal.RssArticleData;
import com.finance.news.repository.NewsArticleRepository;

public final class NewsDuplicateChecker {

    private NewsDuplicateChecker() {
    }

    public static boolean isDuplicate(RssArticleData data, NewsArticleRepository repository) {
        if (data.guid() != null && !data.guid().isBlank()) {
            if (repository.existsByGuid(data.guid())) {
                return true;
            }
        }
        return repository.existsByLink(data.link());
    }
}
