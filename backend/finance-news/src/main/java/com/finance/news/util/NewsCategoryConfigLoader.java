package com.finance.news.util;

import tools.jackson.databind.ObjectMapper;
import com.finance.news.model.NewsCategory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NewsCategoryConfigLoader {

    private static final String KEYWORDS_RESOURCE = "news-category-keywords.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private NewsCategoryConfigLoader() {
    }

    public static NewsCategoryResolverConfig.ResolverConfig load() {
        try (InputStream in = NewsCategoryConfigLoader.class.getClassLoader()
                .getResourceAsStream(KEYWORDS_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Keyword config not found: " + KEYWORDS_RESOURCE);
            }
            NewsCategoryResolverConfig.RawResolverConfig raw =
                    OBJECT_MAPPER.readValue(in, NewsCategoryResolverConfig.RawResolverConfig.class);
            return toResolverConfig(raw);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read keyword config: " + KEYWORDS_RESOURCE, e);
        }
    }

    private static NewsCategoryResolverConfig.ResolverConfig toResolverConfig(
            NewsCategoryResolverConfig.RawResolverConfig raw) {

        Map<NewsCategory, List<String>> categoryKeywords = new LinkedHashMap<>();
        if (raw.categoryKeywords != null) {
            for (Map.Entry<String, List<String>> entry : raw.categoryKeywords.entrySet()) {
                categoryKeywords.put(NewsCategory.valueOf(entry.getKey()), safeList(entry.getValue()));
            }
        }

        NewsCategoryResolverConfig.RuleKeywords rules =
                raw.ruleKeywords == null ? new NewsCategoryResolverConfig.RuleKeywords() : raw.ruleKeywords;

        List<NewsCategory> summaryDiversityCategories = Collections.emptyList();
        if (raw.summaryDiversityCategories != null) {
            summaryDiversityCategories = raw.summaryDiversityCategories.stream()
                    .map(NewsCategory::valueOf)
                    .toList();
        }

        return new NewsCategoryResolverConfig.ResolverConfig(
                categoryKeywords,
                new NewsCategoryResolverConfig.RuleKeywords(
                        safeList(rules.summaryHint),
                        safeList(rules.generalMarketBasket),
                        safeList(rules.nonParityMarketAnchors),
                        safeList(rules.absoluteCrypto),
                        safeList(rules.absoluteParity),
                        safeList(rules.parityPriority),
                        safeList(rules.bondPriority),
                        safeList(rules.bondContext),
                        safeList(rules.macroPolicy),
                        safeList(rules.strongCompanyNews),
                        safeList(rules.definiteCorporate),
                        safeList(rules.foreignBondContext)
                ),
                summaryDiversityCategories
        );
    }

    private static List<String> safeList(List<String> value) {
        return value == null ? Collections.emptyList() : List.copyOf(value);
    }
}
