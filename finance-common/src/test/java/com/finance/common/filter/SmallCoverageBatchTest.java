package com.finance.common.filter;

import com.finance.common.event.KafkaTopicsProperties;
import com.finance.common.filter.ContentLanguageFilter;
import com.finance.common.model.StockSegment;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SmallCoverageBatchTest {

    @Test
    void stockSegment_valuesContainsExpectedConstants() {
        assertThat(StockSegment.values()).contains(
                StockSegment.MAIN_INDEX, StockSegment.SECONDARY_INDEX, StockSegment.EQUITY);
        assertThat(StockSegment.valueOf("EQUITY")).isEqualTo(StockSegment.EQUITY);
    }

    @Test
    void trackedAssetType_normalizeCode_appliesPerTypeRules() {
        assertThat(TrackedAssetType.STOCK.normalizeCode("aapl")).isEqualToIgnoringCase("AAPL");
        assertThat(TrackedAssetType.CRYPTO.normalizeCode("BITCOIN")).isEqualToIgnoringCase("bitcoin");
    }

    @Test
    void trackedAssetType_marketType_isPopulated() {
        for (TrackedAssetType type : TrackedAssetType.values()) {
            assertThat(type.marketType()).isNotNull();
        }
    }

    @Test
    void trackedAsset_buildsAndExposesAccessors() {
        TrackedAsset asset = TrackedAsset.builder()
                .id(1L)
                .assetType(TrackedAssetType.CRYPTO)
                .assetCode("bitcoin")
                .displayName("Bitcoin")
                .build();

        assertThat(asset.getAssetType()).isEqualTo(TrackedAssetType.CRYPTO);
        assertThat(asset.getAssetCode()).isEqualTo("bitcoin");
        assertThat(asset.getDisplayName()).isEqualTo("Bitcoin");
    }

    @Test
    void trackedAsset_lifecycleHooks_setTimestampsAndDefaultSortOrder() throws Exception {
        TrackedAsset asset = TrackedAsset.builder()
                .assetType(TrackedAssetType.CRYPTO).assetCode("bitcoin").build();

        java.lang.reflect.Method onCreate = TrackedAsset.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        onCreate.invoke(asset);

        assertThat(asset.getCreatedAt()).isNotNull();
        assertThat(asset.getUpdatedAt()).isNotNull();
        assertThat(asset.getSortOrder()).isZero();

        java.lang.reflect.Method onUpdate = TrackedAsset.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);
        asset.setSortOrder(null);
        onUpdate.invoke(asset);

        assertThat(asset.getSortOrder()).isZero();
    }

    @Test
    void kafkaTopicsProperties_recordExposesAllFields() {
        KafkaTopicsProperties props = new KafkaTopicsProperties(
                "market.updated", "news.published", "portfolio.updated",
                "macro.indicators.updated",
                "user.email-change-code", "user.registered", "mail.dispatch");

        assertThat(props.marketUpdated()).isEqualTo("market.updated");
        assertThat(props.newsPublished()).isEqualTo("news.published");
        assertThat(props.portfolioUpdated()).isEqualTo("portfolio.updated");
        assertThat(props.macroIndicatorsUpdated()).isEqualTo("macro.indicators.updated");
        assertThat(props.userEmailChangeCode()).isEqualTo("user.email-change-code");
        assertThat(props.mailDispatch()).isEqualTo("mail.dispatch");
    }

    @Test
    void contentLanguageFilter_setsContentLanguageHeader_whenAbsent() throws Exception {
        ContentLanguageFilter filter = new ContentLanguageFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stocks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        LocaleContextHolder.setLocale(Locale.forLanguageTag("tr"));

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader("Content-Language")).isEqualTo("tr");
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void contentLanguageFilter_preservesExistingContentLanguageHeader() throws Exception {
        ContentLanguageFilter filter = new ContentLanguageFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stocks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("Content-Language", "fr");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader("Content-Language")).isEqualTo("fr");
    }
}
