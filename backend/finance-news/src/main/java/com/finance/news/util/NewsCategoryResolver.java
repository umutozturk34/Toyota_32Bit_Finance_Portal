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
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.event.*;
import com.finance.common.repository.*;

import com.finance.news.model.NewsCategory;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.finance.news.util.NewsTextMatcher.buildSearchText;
import static com.finance.news.util.NewsTextMatcher.countMatches;
import static com.finance.news.util.NewsTextMatcher.matchesAny;
import static com.finance.news.util.NewsTextMatcher.scoreKeywords;
import static com.finance.news.util.NewsTextMatcher.tokenize;

@Log4j2
public final class NewsCategoryResolver {

    private static final NewsCategoryResolverConfig.ResolverConfig CONFIG = NewsCategoryConfigLoader.load();

    private static final Map<NewsCategory, List<String>> KEYWORD_MAP = CONFIG.categoryKeywords();
    private static final List<String> SUMMARY_HINT = CONFIG.ruleKeywords().summaryHint();
    private static final List<String> GENERAL_MARKET_BASKET = CONFIG.ruleKeywords().generalMarketBasket();
    private static final List<String> NON_PARITY_ANCHORS = CONFIG.ruleKeywords().nonParityMarketAnchors();
    private static final List<String> ABSOLUTE_CRYPTO = CONFIG.ruleKeywords().absoluteCrypto();
    private static final List<String> ABSOLUTE_PARITY = CONFIG.ruleKeywords().absoluteParity();
    private static final List<String> PARITY_PRIORITY = CONFIG.ruleKeywords().parityPriority();
    private static final List<String> BOND_PRIORITY = CONFIG.ruleKeywords().bondPriority();
    private static final List<String> BOND_CONTEXT = CONFIG.ruleKeywords().bondContext();
    private static final List<String> MACRO_POLICY = CONFIG.ruleKeywords().macroPolicy();
    private static final List<String> STRONG_COMPANY = CONFIG.ruleKeywords().strongCompanyNews();
    private static final List<String> DEFINITIVE_CORPORATE = CONFIG.ruleKeywords().definiteCorporate();
    private static final List<String> FOREIGN_BOND_CONTEXT = CONFIG.ruleKeywords().foreignBondContext();
    private static final List<NewsCategory> SUMMARY_DIVERSITY = CONFIG.summaryDiversityCategories();
    private static final int MIN_SCORE_THRESHOLD = 2;
    private static final int DEFAULT_CATEGORY_BONUS = 3;

    private NewsCategoryResolver() {
    }

    public static NewsCategory resolve(String defaultCategory, String title, String description) {
        String text = buildSearchText(title, description);
        if (text.isBlank()) {
            return null;
        }

        Set<String> tokens = tokenize(text);
        NewsCategory defaultResolved = parseDefaultCategory(defaultCategory);

        NewsCategory strict = resolveByStrictRules(text, tokens, defaultResolved);
        if (strict != null) {
            return strict;
        }

        return resolveByScore(text, tokens, defaultResolved);
    }

    private static NewsCategory resolveByStrictRules(String text, Set<String> tokens,
                                                     NewsCategory defaultResolved) {
        if (matchesAny(text, tokens, ABSOLUTE_CRYPTO)
                || countMatches(text, tokens, KEYWORD_MAP.get(NewsCategory.CRYPTO)) >= 2) {
            return NewsCategory.CRYPTO;
        }

        if (matchesAny(text, tokens, ABSOLUTE_PARITY)
                || countMatches(text, tokens, PARITY_PRIORITY) >= 2) {
            return NewsCategory.PARITE;
        }

        if (shouldClassifyAsBond(text, tokens, defaultResolved, defaultResolved)) {
            return NewsCategory.TAHVIL_BONO;
        }

        if (shouldClassifyAsGeneralFinance(text, tokens)) {
            return NewsCategory.GENEL_FINANS;
        }

        return null;
    }

    private static NewsCategory resolveByScore(String text, Set<String> tokens,
                                               NewsCategory defaultResolved) {
        NewsCategory best = null;
        int bestScore = 0;

        for (Map.Entry<NewsCategory, List<String>> entry : KEYWORD_MAP.entrySet()) {
            if (entry.getKey() == NewsCategory.BORSA_SIRKETLERI
                    && !isStrongCompanyNews(text, tokens)) {
                continue;
            }

            int score = scoreKeywords(text, tokens, entry.getValue());
            if (score == 0) {
                continue;
            }

            if (defaultResolved != null && entry.getKey() == defaultResolved) {
                score += DEFAULT_CATEGORY_BONUS;
            }

            if (score > bestScore) {
                bestScore = score;
                best = entry.getKey();
            }
        }

        int threshold = defaultResolved != null ? 1 : MIN_SCORE_THRESHOLD;
        if (bestScore < threshold) {
            return null;
        }

        return best;
    }

    private static boolean shouldClassifyAsBond(String text, Set<String> tokens,
                                                NewsCategory defaultResolved,
                                                NewsCategory sourceDefault) {
        int priorityMatches = countMatches(text, tokens, BOND_PRIORITY);

        if (priorityMatches > 0) {
            if (hasForeignBondContext(text, tokens)) {
                return false;
            }
            if (hasStrongerCategory(text, tokens, sourceDefault)) {
                return false;
            }
            return true;
        }

        int contextMatches = countMatches(text, tokens, BOND_CONTEXT);
        int macroPolicyMatches = countMatches(text, tokens, MACRO_POLICY);

        if (contextMatches == 0 && macroPolicyMatches > 0) {
            return false;
        }

        if (defaultResolved == NewsCategory.TAHVIL_BONO && contextMatches >= 2) {
            return true;
        }

        return contextMatches >= 3 && !isMixedMarketWithParity(text, tokens);
    }

    private static boolean hasForeignBondContext(String text, Set<String> tokens) {
        return matchesAny(text, tokens, FOREIGN_BOND_CONTEXT);
    }

    private static boolean hasStrongerCategory(String text, Set<String> tokens,
                                               NewsCategory sourceDefault) {
        int bondScore = scoreKeywords(text, tokens, KEYWORD_MAP.get(NewsCategory.TAHVIL_BONO));
        if (sourceDefault == NewsCategory.TAHVIL_BONO) {
            bondScore += DEFAULT_CATEGORY_BONUS;
        }
        for (Map.Entry<NewsCategory, List<String>> entry : KEYWORD_MAP.entrySet()) {
            if (entry.getKey() == NewsCategory.TAHVIL_BONO) continue;
            int score = scoreKeywords(text, tokens, entry.getValue());
            if (score > bondScore) return true;
        }
        return false;
    }

    private static boolean isStrongCompanyNews(String text, Set<String> tokens) {
        if (matchesAny(text, tokens, DEFINITIVE_CORPORATE)) {
            return true;
        }
        return countMatches(text, tokens, STRONG_COMPANY) >= 2;
    }

    private static boolean shouldClassifyAsGeneralFinance(String text, Set<String> tokens) {
        return isMixedMarketWithParity(text, tokens)
                || isBroadMarketRecap(text, tokens)
                || isMarketSummary(text, tokens);
    }

    private static boolean isMixedMarketWithParity(String text, Set<String> tokens) {
        boolean hasParitySignal = countMatches(text, tokens, PARITY_PRIORITY) > 0
                || matchesAny(text, tokens, ABSOLUTE_PARITY);
        boolean hasNonParityAnchor = countMatches(text, tokens, NON_PARITY_ANCHORS) > 0;
        return hasParitySignal && hasNonParityAnchor;
    }

    private static boolean isBroadMarketRecap(String text, Set<String> tokens) {
        return countMatches(text, tokens, GENERAL_MARKET_BASKET) >= 3;
    }

    private static boolean isMarketSummary(String text, Set<String> tokens) {
        if (countMatches(text, tokens, SUMMARY_HINT) == 0) {
            return false;
        }
        return countCategorySignals(text, tokens) >= 3;
    }

    private static int countCategorySignals(String text, Set<String> tokens) {
        int signals = 0;
        for (NewsCategory category : SUMMARY_DIVERSITY) {
            List<String> keywords = KEYWORD_MAP.get(category);
            if (keywords != null && countMatches(text, tokens, keywords) > 0) {
                signals++;
            }
        }
        return signals;
    }

    private static NewsCategory parseDefaultCategory(String defaultCategory) {
        if (defaultCategory == null || defaultCategory.isBlank()) {
            return null;
        }
        try {
            return NewsCategory.valueOf(defaultCategory);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown default news category: {}", defaultCategory);
            return null;
        }
    }
}
