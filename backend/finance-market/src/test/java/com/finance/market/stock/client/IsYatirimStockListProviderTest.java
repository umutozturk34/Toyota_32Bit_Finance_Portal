package com.finance.market.stock.client;

import com.finance.market.stock.config.StockProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IsYatirimStockListProviderTest {

    private IsYatirimStockListProvider provider;

    @BeforeEach
    void setUp() {
        provider = new IsYatirimStockListProvider(new StockProperties());
    }

    @Test
    void extractTickers_returnsEmptyList_whenHtmlIsBlank() {
        List<String> tickers = provider.extractTickers("");

        assertThat(tickers).isEmpty();
    }

    @Test
    void extractTickers_returnsEmptyList_whenHtmlIsNull() {
        List<String> tickers = provider.extractTickers(null);

        assertThat(tickers).isEmpty();
    }

    @Test
    void extractTickers_extractsAllUniqueTickersFromAnchorHrefs() {
        String html = """
                <html><body>
                  <a href="/tr-tr/analiz/hisse/Sayfalar/sirket-karti.aspx?hisse=AKBNK">AKBNK</a>
                  <a href="/tr-tr/analiz/hisse/Sayfalar/sirket-karti.aspx?hisse=GARAN">GARAN</a>
                  <a href="/tr-tr/analiz/hisse/Sayfalar/sirket-karti.aspx?hisse=THYAO">THYAO</a>
                </body></html>
                """;

        List<String> tickers = provider.extractTickers(html);

        assertThat(tickers).containsExactly("AKBNK", "GARAN", "THYAO");
    }

    @Test
    void extractTickers_deduplicatesRepeatedTickers() {
        String html = """
                <a href="?hisse=AKBNK">link1</a>
                <a href="?hisse=AKBNK">link2</a>
                <a href="?hisse=GARAN">link3</a>
                """;

        List<String> tickers = provider.extractTickers(html);

        assertThat(tickers).containsExactly("AKBNK", "GARAN");
    }

    @Test
    void extractTickers_preservesEncounterOrder() {
        String html = "?hisse=THYAO ?hisse=AKBNK ?hisse=GARAN";

        List<String> tickers = provider.extractTickers(html);

        assertThat(tickers).containsExactly("THYAO", "AKBNK", "GARAN");
    }

    @ParameterizedTest
    @CsvSource({
            "'?hisse=AKBNK', AKBNK",
            "'?hisse=GARAN&extra=1', GARAN",
            "'?hisse=THYAO.IS', THYAO",
            "'?hisse=ABC123', ABC123",
    })
    void extractTickers_handlesVariousHrefShapes(String href, String expected) {
        List<String> tickers = provider.extractTickers(href);

        assertThat(tickers).containsExactly(expected);
    }

    @Test
    void extractTickers_ignoresMalformedHrefs() {
        String html = "?hisse= ?hisse=ab ?company=AKBNK href=AKBNK";

        List<String> tickers = provider.extractTickers(html);

        assertThat(tickers).isEmpty();
    }
}
