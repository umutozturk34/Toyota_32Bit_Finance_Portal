package com.finance.market.macro.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.service.MarketAssetProvider;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroIndicator;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Log4j2
public abstract class MacroMarketAssetProvider implements MarketAssetProvider {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("tüfe", "TUFE"),
            Map.entry("tufe", "TUFE"),
            Map.entry("enflasyon", "TUFE"),
            Map.entry("ufe", "UFE"),
            Map.entry("üfe", "UFE"),
            Map.entry("mevduat", "TAS"),
            Map.entry("faiz", "FAIZ"),
            Map.entry("politika", "GLOFFAIZ"),
            Map.entry("interest", "FAIZ"),
            Map.entry("inflation", "TUFE"),
            Map.entry("deposit", "TAS")
    );

    private final MacroIndicatorQueryService queryService;
    private final MacroCategory category;

    protected MacroMarketAssetProvider(MacroIndicatorQueryService queryService, MacroCategory category) {
        this.queryService = queryService;
        this.category = category;
    }

    @Override
    public MarketType getType() {
        return category.instrumentType();
    }

    @Override
    public MarketAssetResponse getByCode(String code) {
        try {
            MacroIndicator indicator = queryService.findByCode(code);
            return indicator.getCategory() == category ? toResponse(indicator) : null;
        } catch (Exception e) {
            log.debug("Macro indicator not found code={}", code);
            return null;
        }
    }

    @Override
    public List<MarketAssetResponse> search(String searchTerm, MarketAssetFilters filters,
                                            String sortBy, String direction, int page, int size) {
        List<MacroIndicator> all = queryService.listByCategory(category);
        String query = searchTerm == null ? "" : searchTerm.trim().toLowerCase(Locale.ROOT);
        String aliasMatch = ALIASES.get(query);
        List<MacroIndicator> filtered = all.stream()
                .filter(m -> matches(m, query, aliasMatch))
                .toList();
        filtered = sort(filtered, sortBy, direction);
        int from = Math.min(page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return filtered.subList(from, to).stream().map(this::toResponse).toList();
    }

    @Override
    public List<MarketAssetResponse> getTopMovers(int limit, boolean gainers) {
        return queryService.listByCategory(category).stream()
                .filter(m -> m.getLastValue() != null)
                .sorted(Comparator.comparing(MacroIndicator::getLastValue,
                        gainers ? Comparator.reverseOrder() : Comparator.naturalOrder()))
                .limit(limit)
                .map(this::toResponse)
                .toList();
    }

    @Override
    public long count(MarketAssetFilters filters) {
        return queryService.listByCategory(category).size();
    }

    @Override
    public long countBySearch(String searchTerm, MarketAssetFilters filters) {
        String query = searchTerm == null ? "" : searchTerm.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return count(filters);
        String aliasMatch = ALIASES.get(query);
        return queryService.listByCategory(category).stream()
                .filter(m -> matches(m, query, aliasMatch))
                .count();
    }

    private boolean matches(MacroIndicator m, String lowerQuery, String aliasMatch) {
        if (lowerQuery.isEmpty()) return true;
        String code = m.getCode() == null ? "" : m.getCode().toLowerCase(Locale.ROOT);
        String label = m.getLabel() == null ? "" : m.getLabel().toLowerCase(Locale.ROOT);
        if (code.contains(lowerQuery) || label.contains(lowerQuery)) return true;
        return aliasMatch != null && code.toUpperCase(Locale.ROOT).contains(aliasMatch);
    }

    private List<MacroIndicator> sort(List<MacroIndicator> list, String sortBy, String direction) {
        Comparator<MacroIndicator> cmp = switch (sortBy != null ? sortBy : "default") {
            case "price" -> Comparator.comparing(MacroIndicator::getLastValue,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "name" -> Comparator.comparing(MacroIndicator::getLabel,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(MacroIndicator::getCode);
        };
        if ("asc".equalsIgnoreCase(direction)) return list.stream().sorted(cmp).toList();
        return list.stream().sorted(cmp.reversed()).toList();
    }

    private MarketAssetResponse toResponse(MacroIndicator m) {
        BigDecimal price = m.getLastValue();
        return new MarketAssetResponse(
                m.getCode(),
                m.getLabel(),
                null,
                category.instrumentType(),
                price,
                null,
                null,
                null,
                null
        );
    }
}
