package com.finance.user.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.finance.user.dto.RecentSearchItem;
import com.finance.user.dto.RecordRecentSearchRequest;
import com.finance.user.model.UserRecentSearch;
import com.finance.user.repository.UserRecentSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads, records and clears a user's recent search selections. Items are stored newest-first as a
 * JSON array, deduplicated by (code, type) case-insensitively and capped to the most recent
 * {@value #MAX_ITEMS}. Macro indicators and blank identifiers are intentionally not stored.
 */
@Service
@RequiredArgsConstructor
public class UserRecentSearchService {

    /** Most recent selections retained per user; older entries are dropped on each new record. */
    static final int MAX_ITEMS = 20;
    private static final String MACRO_PREFIX = "MACRO";
    private static final TypeReference<List<RecentSearchItem>> ITEM_LIST = new TypeReference<>() {};

    private final UserRecentSearchRepository repository;
    private final ObjectMapper objectMapper;

    /** The user's recent searches newest-first, or an empty list when none are stored. */
    @Transactional(readOnly = true)
    public List<RecentSearchItem> getItems(String userSub) {
        return repository.findById(userSub)
                .map(row -> deserialize(row.getItems()))
                .orElseGet(List::of);
    }

    /**
     * Records the selected asset at the front of the user's recent searches: removes any prior entry
     * with the same (code, type) ignoring case, prepends the new entry stamped with the current
     * instant, and caps the list to {@value #MAX_ITEMS} before persisting. Macro types and blank
     * code/type are ignored, returning the unchanged list.
     */
    @Transactional
    public List<RecentSearchItem> record(String userSub, RecordRecentSearchRequest request) {
        String code = request.code() == null ? "" : request.code().trim();
        String type = request.type() == null ? "" : request.type().trim();
        if (code.isEmpty() || type.isEmpty() || isMacro(type)) {
            return getItems(userSub);
        }

        UserRecentSearch row = repository.findById(userSub).orElseGet(() -> UserRecentSearch.emptyFor(userSub));
        List<RecentSearchItem> items = new ArrayList<>(deserialize(row.getItems()));
        items.removeIf(existing -> code.equalsIgnoreCase(existing.code()) && type.equalsIgnoreCase(existing.type()));
        items.add(0, new RecentSearchItem(code, type, request.name(), Instant.now()));
        if (items.size() > MAX_ITEMS) {
            items = new ArrayList<>(items.subList(0, MAX_ITEMS));
        }

        row.setItems(objectMapper.valueToTree(items));
        row.setUpdatedAt(Instant.now());
        repository.save(row);
        return List.copyOf(items);
    }

    /**
     * Removes a single recent search entry matching (code, type) ignoring case and returns the
     * remaining list newest-first; deletes the whole row once the last entry is removed. A no-op
     * (returns the current list unchanged) when the user has no row or no entry matches.
     */
    @Transactional
    public List<RecentSearchItem> remove(String userSub, String code, String type) {
        String c = code == null ? "" : code.trim();
        String tp = type == null ? "" : type.trim();
        UserRecentSearch row = repository.findById(userSub).orElse(null);
        if (row == null || c.isEmpty() || tp.isEmpty()) {
            return getItems(userSub);
        }
        List<RecentSearchItem> items = new ArrayList<>(deserialize(row.getItems()));
        boolean removed = items.removeIf(existing ->
                c.equalsIgnoreCase(existing.code()) && tp.equalsIgnoreCase(existing.type()));
        if (!removed) {
            return List.copyOf(items);
        }
        if (items.isEmpty()) {
            repository.deleteById(userSub);
            return List.of();
        }
        row.setItems(objectMapper.valueToTree(items));
        row.setUpdatedAt(Instant.now());
        repository.save(row);
        return List.copyOf(items);
    }

    /** Clears the user's recent searches by removing their row, leaving an empty list. */
    @Transactional
    public void clear(String userSub) {
        repository.deleteById(userSub);
    }

    private boolean isMacro(String type) {
        return type.toUpperCase().startsWith(MACRO_PREFIX);
    }

    private List<RecentSearchItem> deserialize(JsonNode items) {
        if (items == null || !items.isArray() || items.isEmpty()) {
            return List.of();
        }
        return objectMapper.convertValue(items, ITEM_LIST);
    }
}
