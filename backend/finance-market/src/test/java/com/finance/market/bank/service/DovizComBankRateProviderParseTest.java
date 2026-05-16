package com.finance.market.bank.service;

import com.finance.market.bank.dto.BankRateSnapshot;
import com.finance.market.bank.model.BankRateAssetKind;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DovizComBankRateProviderParseTest {

    private static final String TABLE_HTML = """
            <html><body>
            <table data-sortable>
              <tbody>
                <tr>
                  <td>
                    <a href="https://kur.doviz.com/akbank/amerikan-dolari">
                      <img src="https://cdn.example/akbank.png" alt="Akbank" />
                      Akbank
                    </a>
                  </td>
                  <td data-socket-attr="bid">30,4500</td>
                  <td data-socket-attr="ask">30,8000</td>
                </tr>
                <tr>
                  <td>
                    <a href="/garanti-bbva/amerikan-dolari">
                      <img src="garanti.png" alt="Garanti" />
                      Garanti
                    </a>
                  </td>
                  <td data-socket-attr="bid">30.500,1234</td>
                  <td data-socket-attr="ask">30.900,5678</td>
                </tr>
              </tbody>
            </table>
            </body></html>
            """;

    private static final String MARKET_PAGE_HTML = """
            <html><body>
            <div>
              <span data-socket-key="cumhuriyet-altini" data-socket-attr="bid">44.221,00</span>
              <span data-socket-key="cumhuriyet-altini" data-socket-attr="ask">44.886,00</span>
            </div>
            </body></html>
            """;

    private static final String EMPTY_HTML = "<html><body><div>nothing here</div></body></html>";

    private DovizComBankRateProvider newProvider() {
        return new DovizComBankRateProvider(
                "https://kur.doviz.com",
                "https://altin.doviz.com",
                "test-agent",
                5L,
                10L);
    }

    @SuppressWarnings("unchecked")
    private List<BankRateSnapshot> parse(String html, String code, String name, BankRateAssetKind kind) throws Exception {
        Method m = DovizComBankRateProvider.class
                .getDeclaredMethod("parse", String.class, String.class, String.class, BankRateAssetKind.class);
        m.setAccessible(true);
        return (List<BankRateSnapshot>) m.invoke(newProvider(), html, code, name, kind);
    }

    @Test
    void should_parseBankRowsFromCurrencyTable_when_htmlHasSortableTable() throws Exception {
        List<BankRateSnapshot> rows = parse(TABLE_HTML, "USD", "Amerikan Doları", BankRateAssetKind.CURRENCY);

        assertThat(rows).hasSize(2);
        BankRateSnapshot akbank = rows.stream().filter(r -> r.bankCode().equals("AKBANK")).findFirst().orElseThrow();
        assertThat(akbank.bankName()).isEqualTo("Akbank");
        assertThat(akbank.bankLogoUrl()).isEqualTo("https://cdn.example/akbank.png");
        assertThat(akbank.currencyCode()).isEqualTo("USD");
        assertThat(akbank.assetKind()).isEqualTo(BankRateAssetKind.CURRENCY);
        assertThat(akbank.buyRate()).isEqualByComparingTo(new BigDecimal("30.4500"));
        assertThat(akbank.sellRate()).isEqualByComparingTo(new BigDecimal("30.8000"));
    }

    @Test
    void should_parseTurkishLocaleNumbersWithGroupSeparator_when_priceContainsDotsAndComma() throws Exception {
        List<BankRateSnapshot> rows = parse(TABLE_HTML, "USD", "Amerikan Doları", BankRateAssetKind.CURRENCY);
        BankRateSnapshot garanti = rows.stream().filter(r -> r.bankCode().equals("GARANTI_BBVA")).findFirst().orElseThrow();

        assertThat(garanti.buyRate()).isEqualByComparingTo(new BigDecimal("30500.1234"));
        assertThat(garanti.sellRate()).isEqualByComparingTo(new BigDecimal("30900.5678"));
    }

    @Test
    void should_fallBackToMarketRow_when_goldPageHasNoTable() throws Exception {
        List<BankRateSnapshot> rows = parse(MARKET_PAGE_HTML, "CUMHURIYET_ALTINI", "Cumhuriyet Altını", BankRateAssetKind.GOLD);

        assertThat(rows).hasSize(1);
        BankRateSnapshot market = rows.get(0);
        assertThat(market.bankCode()).isEqualTo("MARKET");
        assertThat(market.bankName()).isEqualTo("Piyasa");
        assertThat(market.bankLogoUrl()).isNull();
        assertThat(market.currencyCode()).isEqualTo("CUMHURIYET_ALTINI");
        assertThat(market.assetKind()).isEqualTo(BankRateAssetKind.GOLD);
        assertThat(market.buyRate()).isEqualByComparingTo(new BigDecimal("44221.00"));
        assertThat(market.sellRate()).isEqualByComparingTo(new BigDecimal("44886.00"));
    }

    @Test
    void should_returnEmpty_when_currencyPageHasNoTable() throws Exception {
        List<BankRateSnapshot> rows = parse(EMPTY_HTML, "USD", "Amerikan Doları", BankRateAssetKind.CURRENCY);

        assertThat(rows).isEmpty();
    }

    @Test
    void should_returnEmpty_when_goldPageHasNoTableAndNoMarketSpan() throws Exception {
        List<BankRateSnapshot> rows = parse(EMPTY_HTML, "GRAM_ALTIN", "Gram Altın", BankRateAssetKind.GOLD);

        assertThat(rows).isEmpty();
    }

    @Test
    void should_exposeSourceIdAsDovizCom() {
        DovizComBankRateProvider provider = newProvider();

        assertThat(provider.sourceId()).isEqualTo("DOVIZ_COM");
    }
}
