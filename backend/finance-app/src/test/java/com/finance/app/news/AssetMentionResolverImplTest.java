package com.finance.app.news;

import com.finance.market.crypto.repository.CryptoRepository;
import com.finance.market.stock.repository.StockRepository;
import com.finance.news.port.AssetMentionResolver.ResolvedAsset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private AssetMentionResolverImpl resolver;

    @BeforeEach
    void setUp() {
        resolver = new AssetMentionResolverImpl(stockRepository, cryptoRepository);
        when(stockRepository.findAllSymbolsAndNames()).thenReturn(List.of(
                new Object[]{"KRVGD.IS", "Kervan Gıda Sanayi ve Ticaret A.Ş."},
                new Object[]{"THYAO.IS", "Türk Hava Yolları A.O."},
                new Object[]{"AKBNK.IS", "Akbank T.A.Ş."},
                new Object[]{"ASELS.IS", "ASELSAN Elektronik Sanayi ve Ticaret A.Ş."},
                new Object[]{"AHGAZ.IS", "Ahlatcı Doğalgaz Dağıtım A.Ş."}
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
    void shouldNotMatchGold_insideAnotherWord() {
        // "altında" (= under) must NOT trigger the "altın" gold keyword — bounded matching protects against it.
        List<ResolvedAsset> result = resolver.resolve("Fiyat 100 liranın altında kaldı", null);

        assertThat(result).isEmpty();
    }
}
