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
 * which the leaf news module can't reach itself. It links an article to every asset it names — STOCKS, CRYPTOS,
 * COMMODITIES and CURRENCIES — by three signals:
 *   1. a parenthesised ticker/symbol — a BIST ticker "(KRVGD)" or a coin symbol "(BTC)" — validated against the
 *      live catalog so unknown acronyms (TCMB, SPK, …) are dropped;
 *   2. the asset NAME — "Kervan Gıda", "Bitcoin" — matched as a whole, accent-insensitive phrase, so an article
 *      that spells the firm/coin out without the ticker still links;
 *   3. a curated Turkish keyword for commodities/currencies — "altın", "petrol", "dolar" — which news names
 *      colloquially, never by their technical code.
 * Stock names use the first two significant words as a distinctive "core" (plus the brand first word); crypto names
 * (usually one proper noun) match on the full name. Stock + crypto catalogs are loaded from finance-market and
 * memoised with a short TTL; the small, stable commodity/currency set is the hand-maintained keyword map below.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class AssetMentionResolverImpl implements AssetMentionResolver {

    private static final Pattern TICKER = Pattern.compile("\\(([A-Z0-9]{3,6})\\)");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> NAME_STOPWORDS = Set.of("ve", "the", "a.s", "as", "ao");
    // First words too generic to match a stock on their OWN (need a second word) — geo/common leads that head many
    // unrelated firms and appear constantly in finance text.
    private static final Set<String> GENERIC_FIRST_WORDS = Set.of(
            "turkiye", "anadolu", "akdeniz", "marmara", "avrupa", "ulusal", "global", "dunya", "merkez");
    // Min length of a stock-name "core" to match on. 5 so distinctive single-word names like "Akbank" (6) link,
    // while still dropping 3-4 char fragments that would over-match.
    private static final int STOCK_CORE_MIN = 5;
    private static final int CRYPTO_NAME_MIN = 4;
    private static final long TTL_MS = 60 * 60 * 1000;

    private final StockRepository stockRepository;
    private final CryptoRepository cryptoRepository;
    private volatile Catalog catalog;
    private volatile long loadedAt;

    /**
     * Curated Turkish keyword → commodity/currency links. The commodity and forex universes are small and stable, and
     * the news always names them by their colloquial Turkish word ("altın", "dolar", "petrol"), never by their
     * technical code/name ("XAUTRYG", "BZ=F", "Altın (Ons)") — so a hand-maintained keyword map links them far more
     * reliably than catalog-name matching ever could. Each is matched as a bounded phrase like every other name, so
     * "altın" hits but "altında" (under) does not, and "euro" / "avro" both reach EUR. Gram gold/silver are the
     * consumer-tracked variants (XAUTRYG/XAGTRYG); the other metals exist only as the ounce contract.
     */
    private static final List<NameRef> KEYWORD_REFS = List.of(
            new NameRef("altin", "XAUTRYG", "COMMODITY"),
            new NameRef("gumus", "XAGTRYG", "COMMODITY"),
            new NameRef("platin", "XPTTRY", "COMMODITY"),
            new NameRef("paladyum", "XPDTRY", "COMMODITY"),
            new NameRef("petrol", "BZ=F", "COMMODITY"),
            new NameRef("brent", "BZ=F", "COMMODITY"),
            new NameRef("bakir", "HG=F", "COMMODITY"),
            new NameRef("bugday", "ZW=F", "COMMODITY"),
            new NameRef("dolar", "USD", "FOREX"),
            new NameRef("euro", "EUR", "FOREX"),
            new NameRef("avro", "EUR", "FOREX"),
            new NameRef("sterlin", "GBP", "FOREX"),
            new NameRef("yen", "JPY", "FOREX"),
            new NameRef("frank", "CHF", "FOREX")
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
                // Also match on the DISTINCTIVE first word alone ("ASELSAN" from "ASELSAN Elektronik…"), since news
                // cites firms by their brand word, not their full legal name. Skipped for generic/geo leads.
                String firstWord = stockNameFirstWord(str(row[1]));
                if (firstWord != null) {
                    byName.add(new NameRef(firstWord, symbol, "STOCK"));
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
        // Only memoise once the market-data catalog has actually loaded. An empty ticker index means stocks/
        // cryptos aren't in the DB yet (a cold `make up` where MarketDataInitializer is still fetching) — caching
        // it would pin a keyword-only matcher for the full TTL, so every article enriched during warm-up would
        // miss its stocks until the next restart. Leaving it uncached makes the next resolve rebuild and pick the
        // catalog up the moment it lands. Keyword refs still resolve in the meantime.
        if (!byTicker.isEmpty()) {
            catalog = built;
            loadedAt = now;
        }
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

    /**
     * The company's brand word — its first significant word — IF it's distinctive enough to match on its own (≥6
     * chars and not a generic/geo lead like "Türkiye"). Returns null otherwise, so such firms only match on their
     * two-word core. This is what links "ASELSAN" to ASELS even though the catalogue name is "ASELSAN Elektronik…".
     */
    private static String stockNameFirstWord(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (String w : normalize(name).split(" ")) {
            if (w.length() <= 1 || NAME_STOPWORDS.contains(w)) {
                continue;
            }
            return (w.length() >= 6 && !GENERIC_FIRST_WORDS.contains(w)) ? w : null;
        }
        return null;
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
