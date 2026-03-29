package com.finance.backend.util;

import com.finance.backend.model.NewsCategory;
import lombok.extern.log4j.Log4j2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Log4j2
public final class NewsCategoryResolver {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private static final Map<NewsCategory, List<String>> KEYWORD_MAP = new LinkedHashMap<>();

    static {
        KEYWORD_MAP.put(NewsCategory.CRYPTO, List.of(
                "bitcoin", "ethereum", "kripto", "btc", "eth", "altcoin",
                "blockchain", "coin", "solana", "ripple", "xrp", "kripto para",
                "stablecoin", "defi", "nft", "binance", "coinbase"));

        KEYWORD_MAP.put(NewsCategory.BORSA_SIRKETLERI, List.of(
                "bilanço", "kâr", "temettü", "bedelsiz", "sermaye artırımı",
                "halka arz", "gong", "hisse geri alım", "kar payı",
                "şirket", "genel kurul", "yönetim kurulu", "spk"));

        KEYWORD_MAP.put(NewsCategory.TAHVIL_BONO, List.of(
                "tahvil", "bono", "kupon", "getiri eğrisi", "hazine",
                "eurobond", "sukuk", "faiz", "merkez bankası", "tcmb",
                "politika faizi", "gösterge faizi", "faiz oranı", "faiz kararı",
                "enflasyon", "tüfe", "para politikası", "ppk"));

        KEYWORD_MAP.put(NewsCategory.FON, List.of(
                "yatırım fonu", "tefas", "portföy", "fon getiri",
                "emeklilik fonu", "borsa yatırım fonu", "bys fon"));

        KEYWORD_MAP.put(NewsCategory.EMTIA, List.of(
                "altın", "petrol", "gümüş", "bakır", "emtia",
                "doğalgaz", "brent", "ons", "gram altın", "çeyrek altın",
                "wti", "opec", "kıymetli maden"));

        KEYWORD_MAP.put(NewsCategory.PARITE, List.of(
                "döviz", "kur", "dolar", "euro", "parite",
                "sterlin", "yen", "tl değer", "dolar/tl", "eur/tl",
                "gbp/tl", "usd/try", "eur/usd", "kur artışı", "kur düşüşü"));

        KEYWORD_MAP.put(NewsCategory.BORSA_ISTANBUL, List.of(
                "borsa", "bist", "endeks", "hisse", "pay piyasası",
                "xu030", "xu100", "viop", "bist 100", "bist100",
                "borsa istanbul", "hisse senedi", "piyasa değeri"));
    }

    private NewsCategoryResolver() {
    }

    public static NewsCategory resolve(String defaultCategory, String title, String description) {
        if (defaultCategory != null && !defaultCategory.isBlank()) {
            return NewsCategory.valueOf(defaultCategory);
        }

        String text = buildSearchText(title, description);

        for (Map.Entry<NewsCategory, List<String>> entry : KEYWORD_MAP.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }

        return NewsCategory.GENEL_FINANS;
    }

    private static String buildSearchText(String title, String description) {
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append(title);
        }
        if (description != null) {
            sb.append(" ").append(description);
        }
        return sb.toString().toLowerCase(TR);
    }
}
