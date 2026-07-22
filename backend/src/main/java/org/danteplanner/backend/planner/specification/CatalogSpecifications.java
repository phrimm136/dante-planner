package org.danteplanner.backend.planner.specification;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerEntityFilter;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilter;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Composable JPA Specifications over the catalog projection. Visibility is row
 * presence, so no predicate here checks published/deleted/taken-down.
 */
public final class CatalogSpecifications {

    private CatalogSpecifications() {
    }

    public static Specification<PlannerCatalog> hasCategory(String category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<PlannerCatalog> isRecommended() {
        return (root, query, cb) -> cb.isTrue(root.get("recommended"));
    }

    /**
     * Free-text search: ngram FULLTEXT relevance on the title OR exact membership
     * in the keyword inverted index. Terms shorter than the ngram token size (2)
     * match no title; boolean-mode operators in the input are neutralized by
     * quoting each term as a phrase.
     */
    public static Specification<PlannerCatalog> matchesQuery(String q) {
        String booleanQuery = toBooleanQuery(q);
        return (root, query, cb) -> cb.or(
                cb.greaterThan(
                        cb.function("match_against", Double.class,
                                root.get("title"), cb.literal(booleanQuery)),
                        0.0),
                keywordExists(root, query, cb, q.trim())
        );
    }

    /**
     * Exact keyword facet via the inverted index.
     */
    public static Specification<PlannerCatalog> hasKeyword(String keyword) {
        return (root, query, cb) -> keywordExists(root, query, cb, keyword);
    }

    /**
     * Content-entity facet via the inverted index.
     */
    public static Specification<PlannerCatalog> containsEntity(ContentEntityType entityType, Integer entityId) {
        return (root, query, cb) -> {
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<PlannerEntityFilter> f = subquery.from(PlannerEntityFilter.class);
            subquery.select(cb.literal(1));
            subquery.where(cb.and(
                    cb.equal(f.get("entityType"), entityType),
                    cb.equal(f.get("entityId"), entityId),
                    cb.equal(f.get("plannerId"), root.get("plannerId"))
            ));
            return cb.exists(subquery);
        };
    }

    private static jakarta.persistence.criteria.Predicate keywordExists(
            Root<PlannerCatalog> root,
            jakarta.persistence.criteria.CommonAbstractCriteria query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            String keyword) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<PlannerKeywordFilter> f = subquery.from(PlannerKeywordFilter.class);
        subquery.select(cb.literal(1));
        subquery.where(cb.and(
                cb.equal(f.get("keyword"), keyword),
                cb.equal(f.get("plannerId"), root.get("plannerId"))
        ));
        return cb.exists(subquery);
    }

    /**
     * Build a boolean-mode query from raw user input: strip FULLTEXT operators by
     * quoting each whitespace-separated term; terms are OR-combined (any match
     * ranks), preserving order-independent multi-word search.
     */
    static String toBooleanQuery(String raw) {
        return Arrays.stream(raw.trim().split("\\s+"))
                .filter(t -> !t.isEmpty())
                .map(t -> '"' + t.replace("\"", "") + '"')
                .collect(Collectors.joining(" "));
    }
}
