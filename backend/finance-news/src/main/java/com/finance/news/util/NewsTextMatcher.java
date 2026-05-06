package com.finance.news.util;
import com.finance.common.service.MarketSnapshotProcessor;

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

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class NewsTextMatcher {

    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final Pattern NON_WORD_SPLITTER = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int SUBSTRING_MATCH_THRESHOLD = 4;

    private NewsTextMatcher() {
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

    public static int scoreKeywords(String text, Set<String> tokens, List<String> keywords) {
        int score = 0;
        for (String keyword : keywords) {
            if (matchesKeyword(text, tokens, keyword)) {
                score += keywordWeight(keyword);
            }
        }
        return score;
    }

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
