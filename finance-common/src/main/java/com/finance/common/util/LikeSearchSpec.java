package com.finance.common.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.Arrays;
import java.util.List;

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
}
