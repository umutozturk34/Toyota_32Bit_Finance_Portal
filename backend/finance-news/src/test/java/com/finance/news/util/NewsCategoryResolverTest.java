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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsCategoryResolverTest {

    @Test
    void absoluteCryptoKeywordReturnsCrypto() {
        NewsCategory result = NewsCategoryResolver.resolve(null,
                "Bitcoin fiyatı yeni rekor kırdı", "Kripto piyasasında hareketli günler");

        assertThat(result).isEqualTo(NewsCategory.CRYPTO);
    }

    @Test
    void twoCryptoKeywordsReturnsCrypto() {
        NewsCategory result = NewsCategoryResolver.resolve(null,
                "Ethereum ve Solana yükselişte", null);

        assertThat(result).isEqualTo(NewsCategory.CRYPTO);
    }

    @Test
    void absoluteParityKeywordReturnsParite() {
        NewsCategory result = NewsCategoryResolver.resolve(null,
                "TCMB döviz kurları güncellendi", null);

        assertThat(result).isEqualTo(NewsCategory.PARITE);
    }

    @Test
    void twoParityPriorityKeywordsReturnParite() {
        NewsCategory result = NewsCategoryResolver.resolve(null,
                "Döviz kuru ve parite hareketleri", null);

        assertThat(result).isEqualTo(NewsCategory.PARITE);
    }

    @Test
    void blankTextReturnsNull() {
        NewsCategory result = NewsCategoryResolver.resolve(null, "", "");

        assertThat(result).isNull();
    }

    @Test
    void nullTextReturnsNull() {
        NewsCategory result = NewsCategoryResolver.resolve(null, null, null);

        assertThat(result).isNull();
    }

    @Test
    void scoreBasedResolutionWithDefaultCategoryBonus() {
        NewsCategory result = NewsCategoryResolver.resolve("EMTIA",
                "Piyasalarda emtia sektöründe gelişmeler", null);

        assertThat(result).isEqualTo(NewsCategory.EMTIA);
    }

    @Test
    void scoreBasedResolutionWithoutDefaultCategoryRequiresMinScore2() {
        NewsCategory result = NewsCategoryResolver.resolve(null,
                "Piyasalarda emtia sektöründe gelişmeler", null);

        assertThat(result).isNull();
    }

    @Test
    void scoreBasedWithDefaultCategoryThreshold1() {
        NewsCategory result = NewsCategoryResolver.resolve("CRYPTO",
                "Dijital varlık haberleri", null);

        assertThat(result).isEqualTo(NewsCategory.CRYPTO);
    }

    @Test
    void bondPriorityKeywordReturnsTahvilBono() {
        NewsCategory result = NewsCategoryResolver.resolve(null,
                "Tahvil piyasasında faizler yükseldi", null);

        assertThat(result).isEqualTo(NewsCategory.TAHVIL_BONO);
    }

    @Test
    void foreignBondContextPreventsClassifyingAsBond() {
        NewsCategory result = NewsCategoryResolver.resolve(null,
                "ABD tahvil faizleri yükseldi treasury yield artış", null);

        assertThat(result).isNotEqualTo(NewsCategory.TAHVIL_BONO);
    }

    @Test
    void mixedMarketWithParityClassifiedAsGenelFinans() {
        NewsCategory result = NewsCategoryResolver.resolve(null,
                "Bist 100 ve döviz kuru günlük performans", null);

        assertThat(result).isEqualTo(NewsCategory.GENEL_FINANS);
    }

    @Test
    void broadMarketRecapWithThreeGeneralBasketKeywords() {
        NewsCategory result = NewsCategoryResolver.resolve(null,
                "Bist 100 gram altın dolar kuru haftalık performans", null);

        assertThat(result).isEqualTo(NewsCategory.GENEL_FINANS);
    }

    @Test
    void invalidDefaultCategoryIgnored() {
        NewsCategory result = NewsCategoryResolver.resolve("INVALID_CATEGORY",
                "Bitcoin ethereum kripto", null);

        assertThat(result).isEqualTo(NewsCategory.CRYPTO);
    }

    @Test
    void borsaIstanbulKeywordsResolveCorrectly() {
        NewsCategory result = NewsCategoryResolver.resolve(null,
                "BIST 100 endeks yükseldi borsa istanbul seans", null);

        assertThat(result).isEqualTo(NewsCategory.BORSA_ISTANBUL);
    }

    @Test
    void emtiaKeywordsResolveCorrectly() {
        NewsCategory result = NewsCategoryResolver.resolve(null,
                "Gram altın fiyatları ve spot altın ons altın rekor", null);

        assertThat(result).isEqualTo(NewsCategory.EMTIA);
    }
}
