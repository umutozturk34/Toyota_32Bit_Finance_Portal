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
 *   3. a curated Turkish keyword for commodities/currencies — "altın", "brent", "dolar" — which news names
 *      colloquially, never by their technical code.
 * Stock names use the first two significant words as a distinctive "core" (plus the brand first word, when it isn't a
 * common/sector word); crypto names (usually one proper noun) match on the full name. Stock + crypto catalogs are
 * loaded from finance-market and memoised with a short TTL; the keyword links, common-word denylist, blocked tickers
 * and thresholds are curated in {@code asset-mention-keywords.json} (see {@link AssetMentionConfig}).
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class AssetMentionResolverImpl implements AssetMentionResolver {

    // A BIST/crypto ticker written on its OWN, not only in parentheses: "ISCTR", "(ISCTR)", "ISCTR'den" all link.
    // It matches a 3-6 char ALL-UPPERCASE token at a word boundary (lookbehind/ahead reject letters/digits, so a
    // Turkish apostrophe-suffix is tolerated and a substring like "DISCTRACK" is not). Title-case words ("Akbank",
    // "Federal") have lowercase letters and never match — only genuine tickers/acronyms do, and the catalog
    // membership + the blocked-acronym denylist (FED, IMF, BIST…) keep it precise.
    private static final Pattern TICKER = Pattern.compile("(?<![\\p{L}\\p{N}])([A-Z][A-Z0-9]{2,5})(?![\\p{L}\\p{N}])");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    // Commodity/currency keyword links, the common-word denylist, blocked tickers, name stopwords and the tuning
    // thresholds are curated in asset-mention-keywords.json (loaded once at class init) so they can be tuned without a
    // code change — the same approach the news classifier uses for news-category-keywords.json. The common-word
    // denylist is what stops a multi-word firm matching on a lone generic word ("Platform Turizm" on every "platform",
    // "Federal-Mogul …" on every "Federal Reserve"); a distinctive coined brand ("Aselsan") is not in it, so it still
    // links on its single word. See AssetMentionConfig for the shape.
    private static final AssetMentionConfig.MentionConfig CONFIG = AssetMentionKeywordsLoader.load();

    // When a two-word name core LEADS with a generic/geo word (Türkiye, Sanayi…), the trailing word must be at
    // least this many normalised chars to count as distinctive — otherwise a 2-3 letter filler ("turkiye is")
    // would anchor matches against unrelated economy text ("Türkiye iş gücü").
    private static final int GENERIC_LEAD_TRAILING_MIN = 4;

    // Chars on each side of a BARE (non-parenthesised) ticker token scanned for lowercase: a deliberate ticker
    // mention sits in normal prose ("ISCTR'den güçlü bilanço"), whereas an all-caps shouting headline word that
    // merely collides with a catalog code ("KENT MERKEZINDE…") has no lowercase neighbour and must not link.
    private static final int TICKER_CONTEXT_WINDOW = 24;

    private final StockRepository stockRepository;
    private final CryptoRepository cryptoRepository;
    private final FundRepository fundRepository;
    private volatile Catalog catalog;
    private volatile long loadedAt;

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
            if (CONFIG.blockedTickers().contains(ticker)) {
                continue;
            }
            // A parenthesised "(ISCTR)" is an unambiguous, deliberate reference. A BARE token is taken as a ticker
            // only when it sits in normal prose (lowercase nearby) — never inside an ALL-CAPS shouting run, where a
            // 3-6 letter word ("KENT", "GLOBAL", "LINK") that collides with a catalog code would otherwise link.
            boolean parenthesised = m.start() > 0 && raw.charAt(m.start() - 1) == '('
                    && m.end() < raw.length() && raw.charAt(m.end()) == ')';
            if (!parenthesised && !hasProseContext(raw, m.start(), m.end())) {
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

    /** True if a lowercase letter sits within {@link #TICKER_CONTEXT_WINDOW} of {@code [start,end)} — i.e. the
     *  all-caps token is embedded in normal prose, not an all-caps headline run. */
    private static boolean hasProseContext(String text, int start, int end) {
        int from = Math.max(0, start - TICKER_CONTEXT_WINDOW);
        int to = Math.min(text.length(), end + TICKER_CONTEXT_WINDOW);
        for (int i = from; i < to; i++) {
            if (Character.isLowerCase(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** Lazily (re)builds the catalog (stock + crypto names/tickers, fund codes), refreshing past the TTL. */
    private Catalog catalog() {
        long now = System.currentTimeMillis();
        Catalog cached = catalog;
        if (cached != null && now - loadedAt <= CONFIG.thresholds().catalogTtlMs()) {
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
                if (name.length() >= CONFIG.thresholds().cryptoNameMin()) {
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
            // Commodity/currency keywords from config; normalised on the way in so an edit can be written in plain
            // Turkish ("altın") yet still match the normalised article text.
            for (AssetMentionConfig.KeywordRef k : CONFIG.commodityCurrencyKeywords()) {
                byName.add(new NameRef(normalize(k.keyword()), k.code(), k.type()));
            }
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
            if (w.length() <= 1 || CONFIG.nameStopwords().contains(w)) {
                continue;
            }
            sig.add(w);
            if (sig.size() == 2) {
                break;
            }
        }
        // A single significant word (a one-word firm name like "Akbank") may match alone only when it's distinctive —
        // a lone common/sector word would over-match, exactly as it does as a brand first word.
        if (sig.size() == 1 && CONFIG.commonNameWords().contains(sig.get(0))) {
            return null;
        }
        // A two-word core that leads with a generic/geo word ("turkiye", "platform"…) is safe only when the
        // trailing word is itself substantial; a 2-3 letter filler turns the core into an everyday phrase
        // ("turkiye is" → matches "Türkiye iş gücü"). A real distinctive trailing word ("Platform Turizm",
        // "Türk Hava") is long enough to stay, so those firms still link by their full name. The single-word
        // guard above already covers a lone generic word; the two-word path previously skipped this entirely.
        if (sig.size() == 2
                && CONFIG.commonNameWords().contains(sig.get(0))
                && sig.get(1).length() < GENERIC_LEAD_TRAILING_MIN) {
            return null;
        }
        String core = String.join(" ", sig);
        return core.length() >= CONFIG.thresholds().stockCoreMin() ? core : null;
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
            if (w.length() <= 1 || CONFIG.nameStopwords().contains(w)) {
                continue;
            }
            return (w.length() >= 6 && !CONFIG.commonNameWords().contains(w)) ? w : null;
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
        if (core.length() < CONFIG.thresholds().fundNameMinChars()
                || core.split(" ").length < CONFIG.thresholds().fundNameMinWords()) {
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
