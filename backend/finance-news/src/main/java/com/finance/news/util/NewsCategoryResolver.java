package com.finance.news.util;


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

/**
 * Keyword-and-rule classifier that maps an article's title/description onto a {@link NewsCategory}.
 * First applies high-confidence strict rules (absolute crypto/parity, bond, general-finance) and falls
 * back to weighted keyword scoring, with a bonus for the source's default category. Keyword and rule
 * lists are loaded once at class init; thresholds are overridable from configuration.
 */
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
    private static int MIN_SCORE_THRESHOLD = 2;
    private static int DEFAULT_CATEGORY_BONUS = 3;

    private NewsCategoryResolver() {
    }

    /** Applies configured score thresholds at startup; package-private so only the config binder calls it. */
    static void overrideThresholds(int minScore, int categoryBonus) {
        MIN_SCORE_THRESHOLD = minScore;
        DEFAULT_CATEGORY_BONUS = categoryBonus;
    }

    /** Classifies an article, applying strict rules first then scoring; returns {@code null} when nothing scores above threshold. */
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

    /** High-confidence overrides applied before scoring: crypto, parity, bond, then general-finance; null if none fire. */
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

    /** Picks the highest-scoring category (gating company news, bonusing the default); null below the threshold. */
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

    /**
     * Bond rule: a priority keyword wins unless it is foreign-bond context or another category scores
     * higher; otherwise requires enough domestic bond-context matches (fewer if the default is bonds)
     * and excludes mixed parity-market recaps.
     */
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

    /** True if any non-bond category outscores the bond score (plus default bonus), so bond should yield. */
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

    /** Gate for company-stock category: a definitive corporate signal, or at least two strong company hints. */
    private static boolean isStrongCompanyNews(String text, Set<String> tokens) {
        if (matchesAny(text, tokens, DEFINITIVE_CORPORATE)) {
            return true;
        }
        return countMatches(text, tokens, STRONG_COMPANY) >= 2;
    }

    /** General-finance rule: mixed parity+other markets, a broad market recap, or a multi-market summary. */
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

    /** True when a summary hint is present and the text spans at least three distinct market categories. */
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
