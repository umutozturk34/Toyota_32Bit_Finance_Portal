package com.finance.market.stock.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UzmanParaIndexConstituentProvider#parse}: extracting constituent tickers from the
 * static index-page HTML. The fixture mirrors the real markup (anchor whitespace included). AAA; no network.
 */
class UzmanParaIndexConstituentProviderTest {

    private static final String INDEX_HTML = """
            <table><tbody>
            <tr> <td class="currency"><a href="/borsa/hisse-senetleri/garanti-bankasi-garan/" target = "_blank" >GARAN</a></td> <td>141,30</td> </tr>
            <tr> <td class="currency"><a href="/borsa/hisse-senetleri/akbank-akbnk/" target = "_blank" >AKBNK</a></td> <td>72,10</td> </tr>
            <tr> <td class="currency"><a href="/borsa/hisse-senetleri/yapi-kredi-ykbnk/" target = "_blank" >YKBNK</a></td> <td>34,00</td> </tr>
            </tbody></table>
            """;

    @Test
    void parse_extractsConstituentTickers_inOrder() {
        // Act
        List<String> tickers = UzmanParaIndexConstituentProvider.parse(INDEX_HTML);

        // Assert
        assertThat(tickers).containsExactly("GARAN", "AKBNK", "YKBNK");
    }

    @Test
    void parse_deduplicates_andIgnoresNonConstituentLinks() {
        // Arrange — a repeated member plus an unrelated nav link that is not a hisse-senetleri anchor
        String html = INDEX_HTML
                + "<li><a href=\"/hisse/teknik-analiz/ISCTR/\" >Teknik Analiz</a></li>"
                + "<tr><td><a href=\"/borsa/hisse-senetleri/garanti-bankasi-garan/\">GARAN</a></td></tr>";

        // Act
        List<String> tickers = UzmanParaIndexConstituentProvider.parse(html);

        // Assert — GARAN once, the nav link excluded
        assertThat(tickers).containsExactly("GARAN", "AKBNK", "YKBNK");
    }

    @Test
    void parse_returnsEmpty_forBlankHtml() {
        // Act + Assert
        assertThat(UzmanParaIndexConstituentProvider.parse("  ")).isEmpty();
    }
}
