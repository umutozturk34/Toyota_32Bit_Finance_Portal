package com.finance.news.util;


import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turkish-aware text matching/scoring primitives for the classifier: lowercasing, tokenization, and
 * keyword matching where short keywords require exact token hits while longer ones allow substring
 * matches. Scoring weights short keywords and phrases more heavily.
 */
public final class NewsTextMatcher {

    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final Pattern NON_WORD_SPLITTER = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static int SUBSTRING_MATCH_THRESHOLD = 4;

    private NewsTextMatcher() {
    }

    /** Sets the keyword-length cutoff above which substring matching replaces exact-token matching. */
    static void overrideSubstringMatchThreshold(int threshold) {
        SUBSTRING_MATCH_THRESHOLD = threshold;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(TR);
    }

    public static String buildSearchText(String title, String description) {
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append(title);
        }
        if (description != null) {
            sb.append(' ').append(description);
        }
        return normalize(sb.toString());
    }

    public static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        for (String token : NON_WORD_SPLITTER.split(text)) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** Matches short keywords as exact tokens (avoiding false substrings) and longer ones as substrings. */
    public static boolean matchesKeyword(String text, Set<String> tokens, String keyword) {
        String normalized = normalize(keyword);
        if (normalized.isBlank()) {
            return false;
        }

        if (normalized.length() < SUBSTRING_MATCH_THRESHOLD) {
            return tokens.contains(normalized);
        }

        return text.contains(normalized);
    }

    public static boolean matchesAny(String text, Set<String> tokens, List<String> keywords) {
        for (String keyword : keywords) {
            if (matchesKeyword(text, tokens, keyword)) {
                return true;
            }
        }
        return false;
    }

    public static int countMatches(String text, Set<String> tokens, List<String> keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (matchesKeyword(text, tokens, keyword)) {
                count++;
            }
        }
        return count;
    }

    /** Sums per-keyword weights for every matching keyword, giving a weighted relevance score. */
    public static int scoreKeywords(String text, Set<String> tokens, List<String> keywords) {
        int score = 0;
        for (String keyword : keywords) {
            if (matchesKeyword(text, tokens, keyword)) {
                score += keywordWeight(keyword);
            }
        }
        return score;
    }

    /** Weight: 3 for very short keywords, 2 for multi-word phrases, 1 for ordinary single words. */
    public static int keywordWeight(String keyword) {
        String normalized = normalize(keyword);
        if (normalized.length() <= 3) {
            return 3;
        }
        if (isPhrase(normalized)) {
            return 2;
        }
        return 1;
    }

    static boolean isPhrase(String keyword) {
        for (int i = 0; i < keyword.length(); i++) {
            if (!Character.isLetterOrDigit(keyword.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
