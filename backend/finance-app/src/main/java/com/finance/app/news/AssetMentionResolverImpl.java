package com.finance.app.news;

import com.finance.market.crypto.repository.CryptoRepository;
import com.finance.market.fund.repository.FundRepository;
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
 * FUNDS, COMMODITIES and CURRENCIES — by three signals:
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
    // Institutional / economic acronyms that read like a parenthesised ticker but never denote a tradable asset —
    // news writes "Avrupa Merkez Bankası (ECB)", "Para Politikası Kurulu (PPK)", "(KDV)". Many collide HEAD-ON with
    // a real money-market fund code (funds coded ECB/PPK/KDV/GDP/IMF/CFO exist), so the parenthesised-ticker signal
    // must drop them outright; such a fund still links via its full name. ASCII-only by design: the ticker regex is
    // [A-Z0-9], so Turkish-letter acronyms (ÖTV, TÜFE, GSYİH) never reach here. Compared upper-cased.
    private static final Set<String> BLOCKED_TICKERS = Set.of(
            // central banks / regulators / bodies
            "ECB", "FED", "FOMC", "IMF", "OPEC", "OECD", "NATO", "TCMB", "SPK", "BDDK", "TMSF", "TUIK", "BIST", "KAP",
            // macro / policy / corporate-title acronyms that appear in finance copy
            "PPK", "KDV", "GDP", "GSYH", "CPI", "PPI", "PMI", "ABD", "CEO", "CFO");
    // First words too generic to match a stock on their OWN (need a second word) — geo/common leads that head many
    // unrelated firms and appear constantly in finance text.
    private static final Set<String> GENERIC_FIRST_WORDS = Set.of(
            "turkiye", "anadolu", "akdeniz", "marmara", "avrupa", "ulusal", "global", "dunya", "merkez");
    // Min length of a stock-name "core" to match on. 5 so distinctive single-word names like "Akbank" (6) link,
    // while still dropping 3-4 char fragments that would over-match.
    private static final int STOCK_CORE_MIN = 5;
    private static final int CRYPTO_NAME_MIN = 4;
    // A fund's long name is only indexed when it's distinctive enough to be safe as a whole phrase: a real fund
    // title runs many words ("Ak Portföy Yeni Teknolojiler Yabancı Hisse Senedi Fonu"), so requiring a long,
    // multi-word phrase keeps a short generic name ("Para Piyasası Fonu") from linking unrelated mentions.
    private static final int FUND_NAME_MIN_CHARS = 18;
    private static final int FUND_NAME_MIN_WORDS = 4;
    private static final long TTL_MS = 60 * 60 * 1000;

    private final StockRepository stockRepository;
    private final CryptoRepository cryptoRepository;
    private final FundRepository fundRepository;
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
            new NameRef("frank", "CHF", "FOREX"),
            // The remaining tracked currencies, by the way TR news actually names them. Distinctive single words
            // (yuan/ruble/manat/tenge/dirhem/won) stand alone; the rest use a bounded multi-word phrase so a generic
            // word (dolar/kron/riyal/dinar/rupi/ley) doesn't over-match (e.g. "kanada dolari" → CAD, not USD).
            new NameRef("yuan", "CNY", "FOREX"),
            new NameRef("ruble", "RUB", "FOREX"),
            new NameRef("won", "KRW", "FOREX"),
            new NameRef("manat", "AZN", "FOREX"),
            new NameRef("tenge", "KZT", "FOREX"),
            new NameRef("dirhem", "AED", "FOREX"),
            new NameRef("kanada dolari", "CAD", "FOREX"),
            new NameRef("avustralya dolari", "AUD", "FOREX"),
            new NameRef("isvec kronu", "SEK", "FOREX"),
            new NameRef("norvec kronu", "NOK", "FOREX"),
            new NameRef("danimarka kronu", "DKK", "FOREX"),
            new NameRef("suudi riyali", "SAR", "FOREX"),
            new NameRef("katar riyali", "QAR", "FOREX"),
            new NameRef("kuveyt dinari", "KWD", "FOREX"),
            new NameRef("pakistan rupisi", "PKR", "FOREX"),
            new NameRef("rumen leyi", "RON", "FOREX")
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
        // `type` keeps that order + the asset type; `count` accumulates how many times the article references the
        // code (ticker occurrences + name-phrase occurrences) — a rough "how prominent" signal shown in the UI.
        Map<String, String> type = new LinkedHashMap<>();
        Map<String, Integer> count = new LinkedHashMap<>();

        Matcher m = TICKER.matcher(raw);
        while (m.find()) {
            String ticker = m.group(1).toUpperCase(Locale.ROOT);
            if (BLOCKED_TICKERS.contains(ticker)) {
                continue;
            }
            CodeType ct = cat.byTicker().get(ticker);
            if (ct != null) {
                type.putIfAbsent(ct.code(), ct.type());
                count.merge(ct.code(), 1, Integer::sum);
            }
        }

        String hay = " " + normalize(raw) + " ";
        // A stock contributes two name refs (2-word core + brand first word) for the SAME code; counting both would
        // double-count the same text, so take the MAX occurrences across a code's refs, then add to its tally.
        Map<String, Integer> nameOcc = new LinkedHashMap<>();
        Map<String, String> nameType = new LinkedHashMap<>();
        for (NameRef ref : cat.byName()) {
            int occ = countOccurrences(hay, " " + ref.core() + " ");
            if (occ > 0) {
                nameOcc.merge(ref.code(), occ, Math::max);
                nameType.putIfAbsent(ref.code(), ref.type());
            }
        }
        nameOcc.forEach((code, occ) -> {
            type.putIfAbsent(code, nameType.get(code));
            count.merge(code, occ, Integer::sum);
        });

        return type.entrySet().stream()
                .map(e -> new ResolvedAsset(e.getKey(), e.getValue(), Math.max(1, count.getOrDefault(e.getKey(), 1))))
                .toList();
    }

    /** Non-overlapping occurrences of {@code needle} in {@code hay}; the shared boundary space is reused so
     *  back-to-back mentions ("akbank akbank") count as two. */
    private static int countOccurrences(String hay, String needle) {
        int hitCount = 0;
        int idx = 0;
        while ((idx = hay.indexOf(needle, idx)) >= 0) {
            hitCount++;
            idx += needle.length() - 1;
        }
        return hitCount;
    }

    /** Lazily (re)builds the catalog (stock + crypto names/tickers, fund codes), refreshing past the TTL. */
    private Catalog catalog() {
        long now = System.currentTimeMillis();
        Catalog cached = catalog;
        if (cached != null && now - loadedAt <= TTL_MS) {
            return cached;
        }
        Map<String, CodeType> byTicker = new LinkedHashMap<>();
        List<NameRef> byName = new ArrayList<>();
        // Pin the catalog ONLY once STOCKS are in the DB. During cold start, news is initialised (and its articles
        // resolved) BEFORE stocks load, but cryptos/funds are already in — so keying the cache on "any ticker"
        // pinned a STOCK-LESS catalog for the full TTL, and even the post-init re-resolution backfill then matched
        // against that stale catalog and never linked any article to a stock. Gating on stocks keeps the catalog
        // un-pinned until they arrive, so the backfill rebuilds it with stocks and links correctly.
        boolean stocksLoaded = false;
        try {
            for (Object[] row : stockRepository.findAllSymbolsAndNames()) {
                String symbol = str(row[0]);
                if (symbol == null || symbol.isBlank()) {
                    continue;
                }
                stocksLoaded = true;
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
            // Funds by their parenthesised short code ("(AFA)") and, when distinctive, their full long name. The
            // shared "Ak Portföy …" prefix makes a 2-word core useless, so a fund's name is matched ONLY as the
            // WHOLE phrase (qualifier tail dropped) — safe because an article that writes a fund's entire title
            // really is about that fund. putIfAbsent keeps a colliding stock/crypto ticker as the winner.
            for (Object[] row : fundRepository.findAllFundCodesAndNames()) {
                String fundCode = str(row[0]);
                if (fundCode == null || fundCode.length() < 3) {
                    continue;
                }
                byTicker.putIfAbsent(fundCode.toUpperCase(Locale.ROOT), new CodeType(fundCode, "FUND"));
                String fundName = fundNameCore(str(row[1]));
                if (fundName != null) {
                    byName.add(new NameRef(fundName, fundCode, "FUND"));
                }
            }
            byName.addAll(KEYWORD_REFS);
        } catch (RuntimeException e) {
            log.warn("Could not build asset catalog for news matching: {}", e.getMessage());
            return cached != null ? cached : new Catalog(Map.of(), List.of());
        }
        Catalog built = new Catalog(byTicker, byName);
        // Only memoise once STOCKS have loaded (see stocksLoaded above). Until then the catalog is rebuilt on every
        // resolve so the moment stocks land — at the latest when the post-init backfill re-resolves — articles pick
        // up their stock links instead of being frozen against a stock-less catalog for the TTL. Crypto/fund/keyword
        // refs still resolve in the meantime.
        if (stocksLoaded) {
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

    /**
     * A fund's long name as a single match phrase, or null when it isn't distinctive enough to be safe. The
     * parenthetical qualifier ("(Hisse Senedi Yoğun Fon)") is dropped first, since news omits it; the remainder is
     * normalised and kept only if it stays long and multi-word — short generic titles are rejected to avoid
     * over-matching. The whole phrase must appear verbatim to link, so only an article naming the fund in full hits.
     */
    private static String fundNameCore(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        int paren = name.indexOf('(');
        String head = paren > 0 ? name.substring(0, paren) : name;
        String core = normalize(head);
        if (core.length() < FUND_NAME_MIN_CHARS || core.split(" ").length < FUND_NAME_MIN_WORDS) {
            return null;
        }
        return core;
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
