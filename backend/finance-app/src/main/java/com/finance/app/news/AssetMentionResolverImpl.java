package com.finance.app.news;

import com.finance.market.stock.repository.StockRepository;
import com.finance.news.port.AssetMentionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The monolith-side implementation of {@link AssetMentionResolver}: it bridges finance-news and finance-market, which
 * the leaf news module can't do itself. It maps an article to the stocks it mentions by TWO signals, both validated
 * against the {@link StockRepository} catalog:
 *   1. the parenthesised ticker Turkish BIST news cites — e.g. "(KRVGD)" — dropping regulator/macro acronyms;
 *   2. the company NAME — e.g. "Kervan Gıda" — matched as a whole, accent-insensitive phrase, so an article that
 *      spells the firm out without the ticker still links.
 * Both indexes are memoised with a short TTL. Name matching uses the first two significant words of each catalogue
 * name as a distinctive "core" and requires it to appear as a bounded phrase, trading some recall for precision.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class AssetMentionResolverImpl implements AssetMentionResolver {

    private static final Pattern TICKER = Pattern.compile("\\(([A-Z0-9]{3,6})\\)");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> NAME_STOPWORDS = Set.of("ve", "the", "a.s", "as", "ao");
    private static final int MIN_CORE_LEN = 7;
    private static final long TTL_MS = 60 * 60 * 1000;

    private final StockRepository stockRepository;
    private volatile Catalog catalog;
    private volatile long loadedAt;

    /** ticker→code (e.g. KRVGD→KRVGD.IS) and the list of (name-core, code) pairs for company-name matching. */
    private record Catalog(Map<String, String> byTicker, List<NameRef> byName) {}
    private record NameRef(String core, String code) {}

    @Override
    public List<ResolvedAsset> resolve(String title, String description) {
        Catalog cat = catalog();
        if (cat.byTicker().isEmpty()) {
            return List.of();
        }
        String raw = (title == null ? "" : title) + " " + (description == null ? "" : description);
        LinkedHashSet<String> codes = new LinkedHashSet<>();

        // (1) parenthesised tickers, validated against the catalog
        Matcher m = TICKER.matcher(raw);
        while (m.find()) {
            String full = cat.byTicker().get(m.group(1).toUpperCase());
            if (full != null) {
                codes.add(full);
            }
        }

        // (2) company names, matched as a bounded phrase in the normalised text
        String hay = " " + normalize(raw) + " ";
        for (NameRef ref : cat.byName()) {
            if (hay.contains(" " + ref.core() + " ")) {
                codes.add(ref.code());
            }
        }

        return codes.stream().map(c -> new ResolvedAsset(c, "STOCK")).toList();
    }

    /** Lazily (re)builds both indexes from the stock catalog, refreshing past the TTL. */
    private Catalog catalog() {
        long now = System.currentTimeMillis();
        Catalog cached = catalog;
        if (cached != null && now - loadedAt <= TTL_MS) {
            return cached;
        }
        Map<String, String> byTicker = new LinkedHashMap<>();
        List<NameRef> byName = new ArrayList<>();
        try {
            for (Object[] row : stockRepository.findAllSymbolsAndNames()) {
                String symbol = row[0] == null ? null : row[0].toString();
                String name = row[1] == null ? null : row[1].toString();
                if (symbol == null || symbol.isBlank()) {
                    continue;
                }
                byTicker.put(symbol.replace(".IS", "").toUpperCase(), symbol);
                String core = nameCore(name);
                if (core != null) {
                    byName.add(new NameRef(core, symbol));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not build stock catalog for news asset matching: {}", e.getMessage());
            return cached != null ? cached : new Catalog(Map.of(), List.of());
        }
        Catalog built = new Catalog(byTicker, byName);
        catalog = built;
        loadedAt = now;
        return built;
    }

    /** The first two significant (non-stopword, len&gt;1) words of a normalised company name; null if too short. */
    private static String nameCore(String name) {
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
        return core.length() >= MIN_CORE_LEN ? core : null;
    }

    /** Lower-case, strip Turkish diacritics (ı/İ → i), reduce punctuation to single spaces. */
    private static String normalize(String text) {
        String lower = text.toLowerCase(java.util.Locale.forLanguageTag("tr")).replace('ı', 'i').replace('İ', 'i');
        String noAccents = DIACRITICS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
        return NON_ALNUM.matcher(noAccents).replaceAll(" ").trim();
    }
}
