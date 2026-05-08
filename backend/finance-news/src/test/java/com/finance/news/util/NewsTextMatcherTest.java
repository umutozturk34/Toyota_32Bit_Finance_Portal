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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NewsTextMatcherTest {

    @Test
    void normalizeConvertsToTurkishLowercase() {
        assertThat(NewsTextMatcher.normalize("İSTANBUL")).isEqualTo("istanbul");
        assertThat(NewsTextMatcher.normalize("DOLAR")).isEqualTo("dolar");
    }

    @Test
    void normalizeNullReturnsEmpty() {
        assertThat(NewsTextMatcher.normalize(null)).isEmpty();
    }

    @Test
    void buildSearchTextConcatenatesTitleAndDescription() {
        String result = NewsTextMatcher.buildSearchText("Bitcoin Yükseldi", "Kripto piyasası hareketli");

        assertThat(result).contains("bitcoin");
        assertThat(result).contains("kripto piyasası hareketli");
    }

    @Test
    void buildSearchTextHandlesNullTitle() {
        String result = NewsTextMatcher.buildSearchText(null, "Açıklama");

        assertThat(result).contains("açıklama");
    }

    @Test
    void buildSearchTextHandlesNullDescription() {
        String result = NewsTextMatcher.buildSearchText("Başlık", null);

        assertThat(result).contains("başlık");
    }

    @Test
    void tokenizeSplitsByNonWordCharacters() {
        Set<String> tokens = NewsTextMatcher.tokenize("bitcoin fiyatı 65000$ seviyesinde");

        assertThat(tokens).contains("bitcoin", "fiyatı", "65000", "seviyesinde");
    }

    @Test
    void tokenizeExcludesBlankTokens() {
        Set<String> tokens = NewsTextMatcher.tokenize("  a   b  ");

        assertThat(tokens).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void matchesKeywordShortTokenExactMatch() {
        Set<String> tokens = Set.of("btc", "eth", "bnb");

        assertThat(NewsTextMatcher.matchesKeyword("btc eth bnb", tokens, "btc")).isTrue();
        assertThat(NewsTextMatcher.matchesKeyword("btc eth bnb", tokens, "xrp")).isFalse();
    }

    @Test
    void matchesKeywordLongTermUsesSubstringMatch() {
        Set<String> tokens = Set.of("kripto", "piyasası", "hareketli");
        String text = "kripto piyasası hareketli";

        assertThat(NewsTextMatcher.matchesKeyword(text, tokens, "kripto piyasa")).isTrue();
    }

    @Test
    void matchesKeywordBlankKeywordReturnsFalse() {
        assertThat(NewsTextMatcher.matchesKeyword("text", Set.of("text"), "")).isFalse();
        assertThat(NewsTextMatcher.matchesKeyword("text", Set.of("text"), "   ")).isFalse();
    }

    @Test
    void matchesAnyReturnsTrueIfAnyMatches() {
        Set<String> tokens = Set.of("bitcoin", "yükseldi");
        String text = "bitcoin yükseldi";

        assertThat(NewsTextMatcher.matchesAny(text, tokens, List.of("ethereum", "bitcoin"))).isTrue();
    }

    @Test
    void matchesAnyReturnsFalseIfNoneMatch() {
        Set<String> tokens = Set.of("dolar", "kuru");
        String text = "dolar kuru";

        assertThat(NewsTextMatcher.matchesAny(text, tokens, List.of("bitcoin", "ethereum"))).isFalse();
    }

    @Test
    void countMatchesCountsAllMatchingKeywords() {
        Set<String> tokens = Set.of("bitcoin", "ethereum", "kripto");
        String text = "bitcoin ethereum kripto";

        int count = NewsTextMatcher.countMatches(text, tokens, List.of("bitcoin", "ethereum", "solana"));

        assertThat(count).isEqualTo(2);
    }

    @Test
    void scoreKeywordsWeightsShortTokensHigher() {
        Set<String> tokens = Set.of("btc", "kripto");
        String text = "btc kripto";

        int score = NewsTextMatcher.scoreKeywords(text, tokens, List.of("btc"));

        assertThat(score).isEqualTo(3);
    }

    @Test
    void scoreKeywordsWeightsPhrasesAt2() {
        Set<String> tokens = Set.of("kripto", "para", "piyasası");
        String text = "kripto para piyasası";

        int score = NewsTextMatcher.scoreKeywords(text, tokens, List.of("kripto para"));

        assertThat(score).isEqualTo(2);
    }

    @Test
    void scoreKeywordsWeightsSingleWordsAt1() {
        Set<String> tokens = Set.of("bitcoin", "yükseldi");
        String text = "bitcoin yükseldi";

        int score = NewsTextMatcher.scoreKeywords(text, tokens, List.of("bitcoin"));

        assertThat(score).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "btc, 3",
            "eth, 3",
            "xrp, 3",
            "kripto para, 2",
            "gram altın, 2",
            "bitcoin, 1",
            "ethereum, 1"
    })
    void keywordWeightReturnsCorrectScore(String keyword, int expectedWeight) {
        assertThat(NewsTextMatcher.keywordWeight(keyword)).isEqualTo(expectedWeight);
    }

    @Test
    void isPhraseDetectsNonAlphanumericCharacters() {
        assertThat(NewsTextMatcher.isPhrase("kripto para")).isTrue();
        assertThat(NewsTextMatcher.isPhrase("dolar/tl")).isTrue();
        assertThat(NewsTextMatcher.isPhrase("bitcoin")).isFalse();
    }

    @Test
    void shortKeywordsBelowThresholdUseExactTokenMatch() {
        Set<String> tokens = Set.of("btc", "xyz");
        assertThat(NewsTextMatcher.matchesKeyword("btc is great", tokens, "btc")).isTrue();
        assertThat(NewsTextMatcher.matchesKeyword("btcusd rising", Set.of("btcusd", "rising"), "btc")).isFalse();
    }

    @Test
    void longKeywordsUseSubstringMatch() {
        Set<String> tokens = Set.of("bitcoin", "piyasası");
        assertThat(NewsTextMatcher.matchesKeyword("bitcoin piyasası hareketli", tokens, "bitcoin piyasa")).isTrue();
        assertThat(NewsTextMatcher.matchesKeyword("bitcoin piyasası hareketli", tokens, "ethereum")).isFalse();
    }
}
