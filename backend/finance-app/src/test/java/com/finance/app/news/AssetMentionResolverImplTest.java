package com.finance.app.news;

import com.finance.market.crypto.repository.CryptoRepository;
import com.finance.market.fund.repository.FundRepository;
import com.finance.market.stock.repository.StockRepository;
import com.finance.news.port.AssetMentionResolver.ResolvedAsset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AssetMentionResolverImpl}: proves a news article is linked to the right stock by BOTH the
 * parenthesised ticker and the company name, validated against the catalog so acronyms are dropped and a
 * ticker+name double-mention collapses to one link. Plain Mockito + AAA; the stock catalog is stubbed.
 */
@ExtendWith(MockitoExtension.class)
class AssetMentionResolverImplTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private CryptoRepository cryptoRepository;
    @Mock
    private FundRepository fundRepository;

    private AssetMentionResolverImpl resolver;

    @BeforeEach
    void setUp() {
        resolver = new AssetMentionResolverImpl(stockRepository, cryptoRepository, fundRepository);
        when(fundRepository.findAllFundCodesAndNames()).thenReturn(List.<Object[]>of(
                new Object[]{"AFA", "Ak Portföy Amerika Yabancı Hisse Senedi Fonu"},
                // Real money-market funds whose codes collide with institutional/economic acronyms.
                new Object[]{"ECB", "Global MD Portföy Para Piyasası (TL) Fonu"},
                new Object[]{"PPK", "QNB Portföy Para Piyasası Katılım (TL) Fonu"},
                new Object[]{"KDV", "Kuveyt Türk Portföy Dokuzuncu Katılım Serbest (Döviz) Fon"}));
        when(stockRepository.findAllSymbolsAndNames()).thenReturn(List.of(
                new Object[]{"KRVGD.IS", "Kervan Gıda Sanayi ve Ticaret A.Ş."},
                new Object[]{"THYAO.IS", "Türk Hava Yolları A.O."},
                new Object[]{"AKBNK.IS", "Akbank T.A.Ş."},
                new Object[]{"ASELS.IS", "ASELSAN Elektronik Sanayi ve Ticaret A.Ş."},
                new Object[]{"AHGAZ.IS", "Ahlatcı Doğalgaz Dağıtım A.Ş."},
                // Firms whose distinctive name LEADS with a common word — they must link only by their full name or
                // ticker, never by the lone first word ("platform", "federal").
                new Object[]{"PLTUR.IS", "Platform Turizm Taşımacılık Gıda A.Ş."},
                new Object[]{"FMIZP.IS", "Federal-Mogul İzmit Piston ve Pim Üretim Tesisleri A.Ş."}
        ));
        when(cryptoRepository.findAllIdsNamesAndSymbols()).thenReturn(List.of(
                new Object[]{"bitcoin", "Bitcoin", "btc"},
                new Object[]{"ethereum", "Ethereum", "eth"}
        ));
    }

    @Test
    void shouldResolveParenthesisedTicker() {
        // Arrange + Act: the ticker appears in parentheses.
        List<ResolvedAsset> result = resolver.resolve("Bir hisseye tedbir kararı (KRVGD)", null);

        // Assert: linked to the full catalog code, typed STOCK.
        assertThat(result).extracting(ResolvedAsset::code).containsExactly("KRVGD.IS");
        assertThat(result).extracting(ResolvedAsset::type).containsExactly("STOCK");
    }

    @Test
    void shouldResolveStockByDistinctiveFirstWord() {
        // "ASELSAN" alone (catalog name is "ASELSAN Elektronik…") links via the distinctive first word.
        List<ResolvedAsset> result = resolver.resolve("ASELSAN ile Akbank en çok işlem gören hisseler oldu", null);

        assertThat(result).extracting(ResolvedAsset::code).contains("ASELS.IS");
    }

    @Test
    void shouldResolveByCompanyName_whenNoTickerPresent() {
        // Arrange + Act: the firm is spelled out, no ticker.
        List<ResolvedAsset> result = resolver.resolve("Türk Hava Yolları yeni sefer açtı", "Detaylar");

        // Assert: matched by name core ("turk hava").
        assertThat(result).extracting(ResolvedAsset::code).containsExactly("THYAO.IS");
    }

    @Test
    void shouldCollapseTickerAndNameToSingleLink() {
        // Arrange + Act: both the name AND the ticker name the same stock.
        List<ResolvedAsset> result = resolver.resolve("Kervan Gıda (KRVGD) temettü ödedi", null);

        // Assert: deduped to one.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("KRVGD.IS");
    }

    @Test
    void shouldCountMultipleMentionsOfSameAsset() {
        // Arrange + Act: Akbank is referenced three ways — name (×2) plus the parenthesised ticker.
        List<ResolvedAsset> result = resolver.resolve("Akbank güçlü bilanço açıkladı; Akbank (AKBNK) yükseldi", null);

        // Assert: one link, mention count reflects the repeated references (name occurrences + ticker).
        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("AKBNK.IS");
        assertThat(result.get(0).mentionCount()).isGreaterThan(1);
    }

    @Test
    void shouldReportSingleMention_whenAssetNamedOnce() {
        // Arrange + Act: a lone reference.
        List<ResolvedAsset> result = resolver.resolve("Türk Hava Yolları yeni sefer açtı", null);

        // Assert: count is exactly one.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).mentionCount()).isEqualTo(1);
    }

    @Test
    void shouldDropUnknownParenthesisedAcronyms() {
        // Arrange + Act: regulator/central-bank acronyms are parenthesised but are not stock codes.
        List<ResolvedAsset> result = resolver.resolve("TCMB faiz kararı (TCMB)", "(SPK) açıklaması");

        // Assert: nothing linked.
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenArticleNamesNoAsset() {
        List<ResolvedAsset> result = resolver.resolve("Piyasalarda sakin bir gün", "Genel piyasa yorumu");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldResolveCryptoByName() {
        // Arrange + Act: a coin spelled out by name, no symbol.
        List<ResolvedAsset> result = resolver.resolve("Bitcoin yeniden 100 bin doların üzerinde", null);

        // Assert: linked to the coin id, typed CRYPTO.
        assertThat(result).extracting(ResolvedAsset::code).containsExactly("bitcoin");
        assertThat(result).extracting(ResolvedAsset::type).containsExactly("CRYPTO");
    }

    @Test
    void shouldResolveCryptoByParenthesisedSymbol() {
        List<ResolvedAsset> result = resolver.resolve("Ethereum (ETH) ağ güncellemesi", null);

        assertThat(result).extracting(ResolvedAsset::code).containsExactly("ethereum");
        assertThat(result).extracting(ResolvedAsset::type).containsExactly("CRYPTO");
    }

    @Test
    void shouldResolveMixedStockAndCrypto() {
        List<ResolvedAsset> result = resolver.resolve("Kervan Gıda (KRVGD) ve Bitcoin gündemde", null);

        assertThat(result).extracting(ResolvedAsset::code).containsExactlyInAnyOrder("KRVGD.IS", "bitcoin");
    }

    @Test
    void shouldResolveMultipleAssetsAcrossTypes_fromOneMarketWrap() {
        // A general "day-end" article naming several firms + gold + a coin links to ALL of them (every article is
        // checked against every catalog entry; one article → many assets).
        List<ResolvedAsset> result = resolver.resolve(
                "Piyasalarda gün sonu: Türk Hava Yolları ve Akbank yükseldi, gram altın rekor kırdı, Bitcoin toparlandı",
                null);

        assertThat(result).extracting(ResolvedAsset::code)
                .contains("THYAO.IS", "AKBNK.IS", "XAUTRYG", "bitcoin");
    }

    @Test
    void shouldResolveGoldKeywordToMetalPair() {
        List<ResolvedAsset> result = resolver.resolve("Gram altın fiyatı rekor kırdı", null);

        assertThat(result).extracting(ResolvedAsset::code).containsExactly("XAUTRYG");
        assertThat(result).extracting(ResolvedAsset::type).containsExactly("COMMODITY");
    }

    @Test
    void shouldNotPinAnEmptyCatalog_soStocksLinkOnceMarketDataLoads() {
        // Cold start: the stock/crypto catalog is still empty when the first article is enriched. That empty
        // catalog must NOT be memoised, otherwise a keyword-only matcher is pinned for the whole TTL and every
        // article ingested during warm-up misses its stocks until a restart. The next resolve after the catalog
        // lands must pick the firm up.
        when(stockRepository.findAllSymbolsAndNames()).thenReturn(List.of());
        when(cryptoRepository.findAllIdsNamesAndSymbols()).thenReturn(List.of());

        List<ResolvedAsset> cold = resolver.resolve("Akbank güçlü bilanço açıkladı", null);
        assertThat(cold).extracting(ResolvedAsset::code).doesNotContain("AKBNK.IS");

        // Market data finishes loading; the SAME resolver instance now sees the populated catalog.
        when(stockRepository.findAllSymbolsAndNames()).thenReturn(List.<Object[]>of(
                new Object[]{"AKBNK.IS", "Akbank T.A.Ş."}));

        List<ResolvedAsset> warm = resolver.resolve("Akbank güçlü bilanço açıkladı", null);
        assertThat(warm).extracting(ResolvedAsset::code).contains("AKBNK.IS");
    }

    @Test
    void shouldNotPinCatalog_whenCryptosLoadedButStocksNotYet_soArticlesLinkOnceStocksLand() {
        // The REAL cold-start order: cryptos/funds (and news) initialise BEFORE stocks. A non-empty crypto catalog
        // must NOT pin a STOCK-LESS catalog for the TTL — otherwise articles resolved in that window (and even the
        // post-init backfill) never link to any stock. Once stocks land, the next resolve must pick the firm up.
        when(stockRepository.findAllSymbolsAndNames()).thenReturn(List.of());
        when(cryptoRepository.findAllIdsNamesAndSymbols()).thenReturn(List.<Object[]>of(
                new Object[]{"bitcoin", "Bitcoin", "BTC"}));

        List<ResolvedAsset> beforeStocks = resolver.resolve("Akbank güçlü bilanço açıkladı", null);
        assertThat(beforeStocks).extracting(ResolvedAsset::code).doesNotContain("AKBNK.IS");

        when(stockRepository.findAllSymbolsAndNames()).thenReturn(List.<Object[]>of(
                new Object[]{"AKBNK.IS", "Akbank T.A.Ş."}));

        List<ResolvedAsset> afterStocks = resolver.resolve("Akbank güçlü bilanço açıkladı", null);
        assertThat(afterStocks).extracting(ResolvedAsset::code).contains("AKBNK.IS");
    }

    @Test
    void shouldNotMatchGold_insideAnotherWord() {
        // "altında" (= under) must NOT trigger the "altın" gold keyword — bounded matching protects against it.
        List<ResolvedAsset> result = resolver.resolve("Fiyat 100 liranın altında kaldı", null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldResolveCurrencyKeywords_toForexPairs() {
        // News names currencies colloquially ("dolar", "avro"), never by code — the keyword map links them to forex.
        List<ResolvedAsset> result = resolver.resolve("Dolar yükselirken avro geriledi", null);

        assertThat(result).extracting(ResolvedAsset::code).containsExactlyInAnyOrder("USD", "EUR");
        assertThat(result).extracting(ResolvedAsset::type).containsOnly("FOREX");
    }

    @Test
    void shouldResolveOilKeyword_toBrentCommodity() {
        List<ResolvedAsset> result = resolver.resolve("Brent petrol varil başına yükseldi", null);

        // "brent" links the Brent contract; bare "petrol" is no longer a keyword (too generic), so the article
        // still yields exactly one link.
        assertThat(result).extracting(ResolvedAsset::code).containsExactly("BZ=F");
        assertThat(result).extracting(ResolvedAsset::type).containsExactly("COMMODITY");
    }

    @Test
    void shouldNotLinkStock_whenOnlyAGenericFirstWordAppears() {
        // PLTUR (Platform Turizm) must NOT link just because an unrelated article uses the common word "platform"
        // (here a crypto platform). Only its full two-word name or its ticker may link it.
        List<ResolvedAsset> result = resolver.resolve(
                "Merkeziyetsiz kripto platform faaliyetlerini genişletti", null);

        assertThat(result).extracting(ResolvedAsset::code).doesNotContain("PLTUR.IS");
    }

    @Test
    void shouldLinkStock_whenItsFullDistinctiveNameAppears() {
        // The same firm DOES link when its distinctive two-word name appears together.
        List<ResolvedAsset> result = resolver.resolve("Platform Turizm yeni sefer ağını açıkladı", null);

        assertThat(result).extracting(ResolvedAsset::code).contains("PLTUR.IS");
    }

    @Test
    void shouldNotLinkStock_whenOnlyACommonLeadWordAppears() {
        // FMIZP (Federal-Mogul İzmit Piston) must NOT link by a lone "Federal" in "Federal Reserve".
        List<ResolvedAsset> result = resolver.resolve("Bitcoin, Federal Reserve kararı öncesi yükseldi", null);

        assertThat(result).extracting(ResolvedAsset::code).doesNotContain("FMIZP.IS");
    }

    @Test
    void shouldNotLinkBrent_whenPetrolAppearsOnlyInACompanyName() {
        // Bare "petrol" inside a firm name ("Metro Petrol") must NOT pull in the Brent contract.
        List<ResolvedAsset> result = resolver.resolve("Metro Petrol ve Tesisleri ünvan değişikliğine gitti", null);

        assertThat(result).extracting(ResolvedAsset::code).doesNotContain("BZ=F");
    }

    @Test
    void shouldResolveFundByParenthesisedCode() {
        // Funds link by their short code in parentheses, validated against the catalog.
        List<ResolvedAsset> result = resolver.resolve("Ak Portföy Amerika Fonu (AFA) güçlü getiri açıkladı", null);

        assertThat(result).extracting(ResolvedAsset::code).contains("AFA");
        assertThat(result).filteredOn(r -> r.code().equals("AFA")).extracting(ResolvedAsset::type).containsExactly("FUND");
    }

    @Test
    void shouldResolveFundByFullLongName() {
        // A fund also links when an article writes its entire long title (no code), matched as a whole phrase.
        List<ResolvedAsset> result = resolver.resolve(
                "Ak Portföy Amerika Yabancı Hisse Senedi Fonu yatırımcısına güçlü getiri sağladı", null);

        assertThat(result).extracting(ResolvedAsset::code).contains("AFA");
        assertThat(result).filteredOn(r -> r.code().equals("AFA")).extracting(ResolvedAsset::type).containsExactly("FUND");
    }

    @Test
    void shouldNotResolveFundFromAPartialOrGenericNameMention() {
        // Only the full title links — a bare "Ak Portföy" company mention must NOT pull in the fund.
        List<ResolvedAsset> result = resolver.resolve("Ak Portföy yeni bir fon halka arz etti", null);

        assertThat(result).extracting(ResolvedAsset::code).doesNotContain("AFA");
    }

    @ParameterizedTest
    @CsvSource({
            "Avrupa Merkez Bankası (ECB) faiz kararını sabit tuttu,           ECB",
            "TCMB Para Politikası Kurulu (PPK) toplantısı sonrası faiz arttı, PPK",
            "Hükümet (KDV) oranını indirdi,                                   KDV",
    })
    void shouldNotResolveInstitutionalAcronymCollidingWithAFundCode(String headline, String blockedCode) {
        // The parenthesised token is an institution/economic acronym (European Central Bank, the central bank's
        // rate-setting committee, value-added tax) — NOT the money-market fund that happens to share the code. The
        // blocklist must drop it even though the fund IS in the catalog.
        List<ResolvedAsset> result = resolver.resolve(headline, null);

        assertThat(result).extracting(ResolvedAsset::code).doesNotContain(blockedCode);
    }

    @Test
    void shouldResolveExtendedCurrencyKeywords() {
        // Distinctive single words for the newly-covered currencies (base form, like the existing dolar/euro keys).
        assertThat(resolver.resolve("yuan güçlenirken ruble değer kaybetti", null))
                .extracting(ResolvedAsset::code).contains("CNY", "RUB");

        // A bounded multi-word phrase links the right currency WITHOUT the generic "dolar" also matching USD.
        List<ResolvedAsset> cad = resolver.resolve("Kanada doları rekor kırdı", null);
        assertThat(cad).extracting(ResolvedAsset::code).contains("CAD");
        assertThat(cad).extracting(ResolvedAsset::code).doesNotContain("USD");
    }

    @Test
    void shouldResolveEveryAssetInARealDayEndWrap_withTurkishCharacters() {
        // The actual "SON DAKİKA: Piyasalar Günü Sert Yükselişle Kapattı" article (news/218), verbatim with
        // its Turkish diacritics: it must link the stocks (Türk Hava Yolları, Akbank, ASELSAN), gold, the
        // dollar and Brent — not just the BIST index. Proves accent-insensitive matching (ı/İ, ş, ç) and that
        // the stock catalog + commodity/currency keywords all fire in ONE pass.
        String body = "Borsa İstanbul'da işlem gören BIST 100 endeksi günü yükselişle tamamladı. En çok işlem "
                + "gören hisseler arasında: Astor Enerji, Türk Hava Yolları, Akbank, ASELSAN ve Türkiye İş "
                + "Bankası (C) yer aldı. Altın rekor seviyelere yaklaştı, ons altın 4.354 dolar oldu. TCMB "
                + "doların efektif kurunu açıkladı. Brent petrol 82,7 dolar seviyesine geriledi.";

        List<ResolvedAsset> result = resolver.resolve("SON DAKİKA: Piyasalar Günü Sert Yükselişle Kapattı", body);

        assertThat(result).extracting(ResolvedAsset::code)
                .contains("THYAO.IS", "AKBNK.IS", "ASELS.IS", "XAUTRYG", "USD", "BZ=F");
    }

    @Test
    void shouldResolveAllAssetTypes_fromOneMarketWrap() {
        // A real day-end wrap names a stock, gold, a currency, oil and a coin at once — every type must link.
        List<ResolvedAsset> result = resolver.resolve(
                "Gün sonu: Akbank yükseldi, gram altın ve Brent petrol rekor kırdı, dolar sabit, Bitcoin toparlandı",
                null);

        assertThat(result).extracting(ResolvedAsset::code)
                .contains("AKBNK.IS", "XAUTRYG", "BZ=F", "USD", "bitcoin");
    }
}
