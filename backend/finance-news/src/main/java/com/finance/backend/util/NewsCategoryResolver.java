package com.finance.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.model.NewsCategory;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Log4j2
public final class NewsCategoryResolver {

    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final String KEYWORDS_RESOURCE = "news-category-keywords.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern NON_WORD_SPLITTER = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final NewsCategoryResolverConfig.ResolverConfig CONFIG = loadConfig();
    private static final Map<NewsCategory, List<String>> KEYWORD_MAP = CONFIG.categoryKeywords();
    private static final List<String> SUMMARY_HINT_KEYWORDS = CONFIG.ruleKeywords().summaryHint();
    private static final List<String> GENERAL_MARKET_BASKET_KEYWORDS = CONFIG.ruleKeywords().generalMarketBasket();
    private static final List<String> NON_PARITY_MARKET_ANCHORS = CONFIG.ruleKeywords().nonParityMarketAnchors();
    private static final List<String> ABSOLUTE_CRYPTO_KEYWORDS = CONFIG.ruleKeywords().absoluteCrypto();
    private static final List<String> ABSOLUTE_PARITY_KEYWORDS = CONFIG.ruleKeywords().absoluteParity();
    private static final List<String> PARITY_PRIORITY_KEYWORDS = CONFIG.ruleKeywords().parityPriority();
    private static final List<String> BOND_PRIORITY_KEYWORDS = CONFIG.ruleKeywords().bondPriority();
    private static final List<String> BOND_CONTEXT_KEYWORDS = CONFIG.ruleKeywords().bondContext();
    private static final List<String> MACRO_POLICY_KEYWORDS = CONFIG.ruleKeywords().macroPolicy();
    private static final List<String> STRONG_COMPANY_NEWS_KEYWORDS = CONFIG.ruleKeywords().strongCompanyNews();
    private static final List<NewsCategory> SUMMARY_DIVERSITY_CATEGORIES = CONFIG.summaryDiversityCategories();

    private static NewsCategoryResolverConfig.ResolverConfig loadConfig() {
        try (InputStream in = NewsCategoryResolver.class.getClassLoader().getResourceAsStream(KEYWORDS_RESOURCE)) {
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
                        safeList(rules.strongCompanyNews)
                ),
                summaryDiversityCategories
        );
    }

    private static List<String> safeList(List<String> value) {
        return value == null ? Collections.emptyList() : List.copyOf(value);
    }

    private NewsCategoryResolver() {
    }

    public static NewsCategory resolve(String defaultCategory, String title, String description) {
        String text = buildSearchText(title, description);
        if (text.isBlank()) {
            return null;
        }

        Set<String> tokens = tokenize(text);
        NewsCategory defaultResolved = parseDefaultCategory(defaultCategory);

        NewsCategory strictCategory = resolveByStrictRules(text, tokens, defaultResolved);
        if (strictCategory != null) {
            return strictCategory;
        }

        return resolveByScore(text, tokens, defaultResolved);
    }

    private static NewsCategory resolveByStrictRules(String text, Set<String> tokens, NewsCategory defaultResolved) {
        if (matchesAny(text, tokens, ABSOLUTE_CRYPTO_KEYWORDS)
                || countMatches(text, tokens, KEYWORD_MAP.get(NewsCategory.CRYPTO)) >= 2) {
            return NewsCategory.CRYPTO;
        }

        if (matchesAny(text, tokens, ABSOLUTE_PARITY_KEYWORDS)
                || countMatches(text, tokens, PARITY_PRIORITY_KEYWORDS) >= 2) {
            return NewsCategory.PARITE;
        }

        if (shouldClassifyAsBond(text, tokens, defaultResolved)) {
            return NewsCategory.TAHVIL_BONO;
        }

        if (shouldClassifyAsGeneralFinance(text, tokens)) {
            return NewsCategory.GENEL_FINANS;
        }

        return null;
    }

    private static NewsCategory resolveByScore(String text, Set<String> tokens, NewsCategory defaultResolved) {
        NewsCategory bestCategory = null;
        int bestScore = 0;

        for (Map.Entry<NewsCategory, List<String>> entry : KEYWORD_MAP.entrySet()) {
            if (entry.getKey() == NewsCategory.BORSA_SIRKETLERI
                    && !isStrongCompanyNews(text, tokens)) {
                continue;
            }

            int score = scoreCategory(text, tokens, entry.getValue());
            if (score == 0) {
                continue;
            }

            if (defaultResolved != null && entry.getKey() == defaultResolved) {
                score += 1;
            }

            if (score > bestScore) {
                bestScore = score;
                bestCategory = entry.getKey();
            }
        }

        return bestCategory;
    }

    private static int scoreCategory(String text, Set<String> tokens, List<String> keywords) {
        int score = 0;
        for (String keyword : keywords) {
            if (matchesKeyword(text, tokens, keyword)) {
                score += keywordWeight(keyword);
            }
        }
        return score;
    }

    private static boolean matchesAny(String text, Set<String> tokens, List<String> keywords) {
        return countMatches(text, tokens, keywords) > 0;
    }

    private static int countMatches(String text, Set<String> tokens, List<String> keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (matchesKeyword(text, tokens, keyword)) {
                count++;
            }
        }
        return count;
    }

    private static boolean matchesKeyword(String text, Set<String> tokens, String keyword) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isBlank()) {
            return false;
        }

        if (normalizedKeyword.length() <= 3) {
            return tokens.contains(normalizedKeyword);
        }

        if (isPhraseKeyword(normalizedKeyword)) {
            return containsAsBoundaryPhrase(text, normalizedKeyword);
        }

        return tokens.contains(normalizedKeyword);
    }

    private static boolean containsAsBoundaryPhrase(String text, String phrase) {
        Pattern pattern = Pattern.compile("(^|[^\\p{L}\\p{N}])" + Pattern.quote(phrase) + "([^\\p{L}\\p{N}]|$)");
        return pattern.matcher(text).find();
    }

    private static boolean isPhraseKeyword(String keyword) {
        for (int i = 0; i < keyword.length(); i++) {
            char ch = keyword.charAt(i);
            if (!Character.isLetterOrDigit(ch)) {
                return true;
            }
        }
        return false;
    }

    private static int keywordWeight(String keyword) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.length() <= 3) {
            return 3;
        }
        if (isPhraseKeyword(normalizedKeyword)) {
            return 2;
        }
        return 1;
    }

    private static boolean isMarketSummary(String text, Set<String> tokens) {
        if (countMatches(text, tokens, SUMMARY_HINT_KEYWORDS) == 0) {
            return false;
        }

        return countCategorySignals(text, tokens) >= 3;
    }

    private static boolean isBroadMarketRecap(String text, Set<String> tokens) {
        return countMatches(text, tokens, GENERAL_MARKET_BASKET_KEYWORDS) >= 3;
    }

    private static boolean shouldClassifyAsGeneralFinance(String text, Set<String> tokens) {
        return isMixedMarketWithParity(text, tokens)
                || isBroadMarketRecap(text, tokens)
                || isMarketSummary(text, tokens);
    }

    private static boolean shouldClassifyAsBond(String text, Set<String> tokens, NewsCategory defaultResolved) {
        int priorityMatches = countMatches(text, tokens, BOND_PRIORITY_KEYWORDS);
        if (priorityMatches > 0) {
            return true;
        }

        int contextMatches = countMatches(text, tokens, BOND_CONTEXT_KEYWORDS);
        int macroPolicyMatches = countMatches(text, tokens, MACRO_POLICY_KEYWORDS);

        if (contextMatches == 0 && macroPolicyMatches > 0) {
            return false;
        }

        if (defaultResolved == NewsCategory.TAHVIL_BONO && contextMatches >= 2) {
            return true;
        }

        return contextMatches >= 3 && !isMixedMarketWithParity(text, tokens);
    }

    private static boolean isMixedMarketWithParity(String text, Set<String> tokens) {
        boolean hasParitySignal = countMatches(text, tokens, PARITY_PRIORITY_KEYWORDS) > 0
                || matchesAny(text, tokens, ABSOLUTE_PARITY_KEYWORDS);
        boolean hasNonParityAnchor = countMatches(text, tokens, NON_PARITY_MARKET_ANCHORS) > 0;
        return hasParitySignal && hasNonParityAnchor;
    }

    private static int countCategorySignals(String text, Set<String> tokens) {
        int categorySignals = 0;
        for (NewsCategory category : SUMMARY_DIVERSITY_CATEGORIES) {
            List<String> keywords = KEYWORD_MAP.get(category);
            if (keywords != null && countMatches(text, tokens, keywords) > 0) {
                categorySignals++;
            }
        }
        return categorySignals;
    }

    private static boolean isStrongCompanyNews(String text, Set<String> tokens) {
        return countMatches(text, tokens, STRONG_COMPANY_NEWS_KEYWORDS) >= 1;
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

    private static String buildSearchText(String title, String description) {
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append(title);
        }
        if (description != null) {
            sb.append(" ").append(description);
        }
        return normalize(sb.toString());
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(TR);
    }

    private static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        for (String token : NON_WORD_SPLITTER.split(text)) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
