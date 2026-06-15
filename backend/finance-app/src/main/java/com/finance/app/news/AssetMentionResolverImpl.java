package com.finance.app.news;

import com.finance.market.crypto.repository.CryptoRepository;
import com.finance.market.stock.repository.StockRepository;
import com.finance.news.port.AssetMentionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The monolith-side implementation of {@link AssetMentionResolver}: it bridges finance-news with finance-market,
 * which the leaf news module can't reach itself. It links an article to the STOCKS and CRYPTOS it names, by two
 * signals, both validated against the live catalogs:
 *   1. a parenthesised ticker/symbol — a BIST ticker "(KRVGD)" or a coin symbol "(BTC)" — dropping unknown acronyms
 *      (TCMB, SPK, …);
 *   2. the asset NAME — "Kervan Gıda", "Bitcoin" — matched as a whole, accent-insensitive phrase, so an article
 *      that spells the firm/coin out without the ticker still links.
 * Stock names use the first two significant words as a distinctive "core"; crypto names (usually one proper noun)
 * match on the full name. Both catalogs are memoised with a short TTL.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class AssetMentionResolverImpl implements AssetMentionResolver {

    private static final Pattern TICKER = Pattern.compile("\\(([A-Z0-9]{3,6})\\)");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> NAME_STOPWORDS = Set.of("ve", "the", "a.s", "as", "ao");
    private static final int STOCK_CORE_MIN = 7;
    private static final int CRYPTO_NAME_MIN = 4;
    private static final long TTL_MS = 60 * 60 * 1000;

    private final StockRepository stockRepository;
    private final CryptoRepository cryptoRepository;
    private volatile Catalog catalog;
    private volatile long loadedAt;

    /**
     * Curated Turkish keyword → precious-metal links. Gram gold/silver are COMMODITY-type assets (XAUTRYG/XAGTRYG).
     * Matched as a bounded phrase like every other name, so "altın" hits but "altında" (under) does not.
     */
    private static final List<NameRef> KEYWORD_REFS = List.of(
            new NameRef("altin", "XAUTRYG", "COMMODITY"),
            new NameRef("gumus", "XAGTRYG", "COMMODITY")
    );

    /** ticker/symbol → (code, type), plus the (name-phrase, code, type) list for name matching. */
    private record Catalog(Map<String, CodeType> byTicker, List<NameRef> byName) {}
    private record CodeType(String code, String type) {}
    private record NameRef(String core, String code, String type) {}

    @Override
    public List<ResolvedAsset> resolve(String title, String description) {
        Catalog cat = catalog();
        if (cat.byTicker().isEmpty() && cat.byName().isEmpty()) {
            return List.of();
        }
        String raw = (title == null ? "" : title) + " " + (description == null ? "" : description);
        // Dedup by code (a coin named both "(BTC)" and "Bitcoin" links once); insertion order = ticker hits first.
        Map<String, String> hits = new LinkedHashMap<>();

        Matcher m = TICKER.matcher(raw);
        while (m.find()) {
            CodeType ct = cat.byTicker().get(m.group(1).toUpperCase(Locale.ROOT));
            if (ct != null) {
                hits.putIfAbsent(ct.code(), ct.type());
            }
        }

        String hay = " " + normalize(raw) + " ";
        for (NameRef ref : cat.byName()) {
            if (hay.contains(" " + ref.core() + " ")) {
                hits.putIfAbsent(ref.code(), ref.type());
            }
        }

        return hits.entrySet().stream().map(e -> new ResolvedAsset(e.getKey(), e.getValue())).toList();
    }

    /** Lazily (re)builds both catalogs (stock + crypto), refreshing past the TTL. */
    private Catalog catalog() {
        long now = System.currentTimeMillis();
        Catalog cached = catalog;
        if (cached != null && now - loadedAt <= TTL_MS) {
            return cached;
        }
        Map<String, CodeType> byTicker = new LinkedHashMap<>();
        List<NameRef> byName = new ArrayList<>();
        try {
            for (Object[] row : stockRepository.findAllSymbolsAndNames()) {
                String symbol = str(row[0]);
                if (symbol == null || symbol.isBlank()) {
                    continue;
                }
                byTicker.put(symbol.replace(".IS", "").toUpperCase(Locale.ROOT), new CodeType(symbol, "STOCK"));
                String core = stockNameCore(str(row[1]));
                if (core != null) {
                    byName.add(new NameRef(core, symbol, "STOCK"));
                }
            }
            for (Object[] row : cryptoRepository.findAllIdsNamesAndSymbols()) {
                String id = str(row[0]);
                if (id == null || id.isBlank()) {
                    continue;
                }
                String symbol = str(row[2]);
                if (symbol != null && symbol.length() >= 3) {
                    byTicker.putIfAbsent(symbol.toUpperCase(Locale.ROOT), new CodeType(id, "CRYPTO"));
                }
                String name = normalize(str(row[1]));
                if (name.length() >= CRYPTO_NAME_MIN) {
                    byName.add(new NameRef(name, id, "CRYPTO"));
                }
            }
            byName.addAll(KEYWORD_REFS);
        } catch (RuntimeException e) {
            log.warn("Could not build asset catalog for news matching: {}", e.getMessage());
            return cached != null ? cached : new Catalog(Map.of(), List.of());
        }
        Catalog built = new Catalog(byTicker, byName);
        catalog = built;
        loadedAt = now;
        return built;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    /** The first two significant (non-stopword, len&gt;1) words of a normalised company name; null if too short. */
    private static String stockNameCore(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        List<String> sig = new ArrayList<>(2);
        for (String w : normalize(name).split(" ")) {
            if (w.length() <= 1 || NAME_STOPWORDS.contains(w)) {
                continue;
            }
            sig.add(w);
            if (sig.size() == 2) {
                break;
            }
        }
        String core = String.join(" ", sig);
        return core.length() >= STOCK_CORE_MIN ? core : null;
    }

    /** Lower-case, strip Turkish diacritics (ı/İ → i), reduce punctuation to single spaces. */
    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.forLanguageTag("tr")).replace('ı', 'i').replace('İ', 'i');
        String noAccents = DIACRITICS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
        return NON_ALNUM.matcher(noAccents).replaceAll(" ").trim();
    }
}
