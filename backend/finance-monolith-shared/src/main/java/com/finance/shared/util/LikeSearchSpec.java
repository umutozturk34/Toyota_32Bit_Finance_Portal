package com.finance.shared.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class LikeSearchSpec {

    private LikeSearchSpec() {
    }

    public static <T> Predicate byFieldsContains(Root<T> root, CriteriaBuilder cb,
                                                  String term, String... fields) {
        return byFieldsContains(root, cb, term, Arrays.asList(fields));
    }

    public static <T> Predicate byFieldsContains(Root<T> root, CriteriaBuilder cb,
                                                  String term, List<String> fields) {
        String pattern = "%" + term.toLowerCase() + "%";
        Predicate[] predicates = fields.stream()
                .map(field -> {
                    Path<String> path = root.get(field);
                    return cb.like(cb.lower(path), pattern);
                })
                .toArray(Predicate[]::new);
        return cb.or(predicates);
    }

    public static <T> Predicate byFieldsContainsAllTokensUnaccent(Root<T> root, CriteriaBuilder cb,
                                                                   String term, String... fields) {
        if (term == null || term.isBlank() || fields == null || fields.length == 0) {
            return cb.conjunction();
        }
        String[] tokens = term.toLowerCase(Locale.ROOT).trim().split("\\s+");
        Predicate[] tokenPreds = new Predicate[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            String pattern = "%" + tokens[i] + "%";
            Predicate[] fieldPreds = new Predicate[fields.length];
            for (int j = 0; j < fields.length; j++) {
                Expression<String> normalizedField = cb.function("unaccent", String.class, cb.lower(root.get(fields[j])));
                Expression<String> normalizedPattern = cb.function("unaccent", String.class, cb.literal(pattern));
                fieldPreds[j] = cb.like(normalizedField, normalizedPattern);
            }
            tokenPreds[i] = cb.or(fieldPreds);
        }
        return cb.and(tokenPreds);
    }
}
