package com.finance.news.service.source;

import com.finance.news.model.NewsArticle;
import com.finance.news.port.AssetMentionResolver;
import com.finance.news.port.AssetMentionResolver.ResolvedAsset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NewsAssetEnricher}: the matcher must see the FULL body, since a market wrap names firms and
 * tickers only deep in {@code content} while the description carries just the lead sentence. Resolver + provider are
 * stubbed; plain Mockito + AAA.
 */
class NewsAssetEnricherTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AssetMentionResolver> providerOf(AssetMentionResolver resolver) {
        ObjectProvider<AssetMentionResolver> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(resolver);
        return provider;
    }

    @Test
    void shouldSearchTheFullContent_notJustTheDescription() {
        // Arrange: the description names only the index; the firm + ticker live in the body.
        AssetMentionResolver resolver = mock(AssetMentionResolver.class);
        when(resolver.resolve(any(), any())).thenReturn(List.of(new ResolvedAsset("AKBNK.IS", "STOCK")));
        NewsAssetEnricher enricher = new NewsAssetEnricher(providerOf(resolver));
        NewsArticle article = NewsArticle.builder()
                .title("Borsa İstanbul'da haftanın kazananları")
                .description("BIST 100 endeksi haftayı yükselişle kapattı.")
                .content("En çok işlem gören hisseler: Akbank (AKBNK), Türk Hava Yolları (THYAO).")
                .build();

        // Act
        boolean enriched = enricher.enrich(article);

        // Assert: the body passed to the resolver carries BOTH the description and the content.
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(resolver).resolve(eq(article.getTitle()), body.capture());
        assertThat(body.getValue()).contains("BIST 100 endeksi").contains("Akbank (AKBNK)");
        assertThat(enriched).isTrue();
        assertThat(article.getAssets()).extracting(a -> a.getAssetCode()).containsExactly("AKBNK.IS");
    }

    @Test
    void shouldTolerateNullDescriptionOrContent() {
        AssetMentionResolver resolver = mock(AssetMentionResolver.class);
        when(resolver.resolve(any(), any())).thenReturn(List.of());
        NewsAssetEnricher enricher = new NewsAssetEnricher(providerOf(resolver));
        NewsArticle article = NewsArticle.builder()
                .title("Başlık")
                .description(null)
                .content("Akbank (AKBNK) güçlü bilanço açıkladı.")
                .build();

        boolean enriched = enricher.enrich(article);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(resolver).resolve(eq("Başlık"), body.capture());
        assertThat(body.getValue()).isEqualTo("Akbank (AKBNK) güçlü bilanço açıkladı.");
        assertThat(enriched).isFalse();
    }
}
