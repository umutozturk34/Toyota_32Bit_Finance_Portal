package com.finance.market.stock.client;

import com.finance.market.stock.dto.external.CompanyCardDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IsYatirimCompanyCardProvider}'s pure parsing of the İş Yatırım company-card HTML.
 * The fixture mirrors the real server-rendered markup (künye {@code <th>/<td>} rows and the weighted-index
 * table). AAA throughout; no network — only the static {@code parse}/{@code bareTicker} logic is exercised.
 */
class IsYatirimCompanyCardProviderTest {

    // Trimmed but structurally faithful copy of the real GARAN company card.
    private static final String CARD_HTML = """
            <table><tbody>
            <tr> <th>Ünvanı</th> <td class="text-right">Garanti Bankası </td> </tr>
            <tr> <th>Kuruluş</th> <td class="text-right">25.04.1946</td> </tr>
            <tr class="companyActivityArea"> <th>Faal Alanı</th> <td class="text-right">Ticari bankacılık</td> </tr>
            <tr> <th>Telefon</th> <td class="text-right">(0212)3181818</td> </tr>
            <tr> <th>Adres</th> <td class="text-right">LEVENT AYTAR CADDESİ NO:2 BEŞİKTAŞ/İSTANBUL</td> </tr>
            </tbody></table>
            <div class="box-title"><h3>Dahil Olduğu Endekslerdeki Ağırlığı</h3></div>
            <div class="box-content"><div class="table horizontal"><table>
            <thead><tr><th class="text-right">XU100 (%)</th><th class="text-right">XU050 (%)</th>\
            <th class="text-right">XU030 (%)</th></tr></thead>
            <tbody><tr><td class="text-right">1,8</td><td class="text-right">2,2</td>\
            <td class="text-right">2,5</td></tr></tbody>
            </table></div></div>
            """;

    @Test
    void parse_extractsKunyeFields() {
        // Act
        CompanyCardDto card = IsYatirimCompanyCardProvider.parse(CARD_HTML);

        // Assert
        assertThat(card).isNotNull();
        assertThat(card.legalName()).isEqualTo("Garanti Bankası");
        assertThat(card.sector()).isEqualTo("Ticari bankacılık");
        assertThat(card.foundedDate()).isEqualTo(LocalDate.of(1946, 4, 25));
        assertThat(card.city()).isEqualTo("İSTANBUL");
    }

    @Test
    void parse_pairsEachIndexWithItsWeight_inColumnOrder() {
        // Act
        CompanyCardDto card = IsYatirimCompanyCardProvider.parse(CARD_HTML);

        // Assert — codes from the header row, weights from the data row, Turkish comma decimals parsed
        assertThat(card.indexWeights()).hasSize(3);
        assertThat(card.indexWeights()).extracting(CompanyCardDto.IndexWeight::indexCode)
                .containsExactly("XU100", "XU050", "XU030");
        assertThat(card.indexWeights().get(0).weight()).isEqualByComparingTo("1.8");
        assertThat(card.indexWeights().get(2).weight()).isEqualByComparingTo("2.5");
    }

    @Test
    void parse_returnsEmptyMemberships_whenIndexTableAbsent() {
        // Arrange — a card with künye but no index-weight section
        String html = "<table><tbody><tr><th>Ünvanı</th><td>Acme A.Ş.</td></tr></tbody></table>";

        // Act
        CompanyCardDto card = IsYatirimCompanyCardProvider.parse(html);

        // Assert
        assertThat(card.legalName()).isEqualTo("Acme A.Ş.");
        assertThat(card.indexWeights()).isEmpty();
    }

    @Test
    void parse_returnsNull_forBlankHtml() {
        // Act + Assert
        assertThat(IsYatirimCompanyCardProvider.parse("  ")).isNull();
    }

    @Test
    void bareTicker_stripsExchangeSuffix() {
        // Act + Assert
        assertThat(IsYatirimCompanyCardProvider.bareTicker("GARAN.IS")).isEqualTo("GARAN");
        assertThat(IsYatirimCompanyCardProvider.bareTicker("garan")).isEqualTo("GARAN");
        assertThat(IsYatirimCompanyCardProvider.bareTicker(null)).isEmpty();
    }
}
