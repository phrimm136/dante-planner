package org.danteplanner.backend.specification;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Root;
import org.danteplanner.backend.entity.ContentEntityType;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerContentIndex;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable JPA Specifications for querying published planners.
 * Each method returns a self-contained predicate that can be AND-composed.
 */
public final class PlannerSpecifications {

    private PlannerSpecifications() {
    }

    /**
     * Visible published planners: published, not soft-deleted, not taken down.
     */
    public static Specification<Planner> isPublished() {
        return (root, query, cb) -> cb.and(
                cb.isTrue(root.get("published")),
                cb.isNull(root.get("deletedAt")),
                cb.isNull(root.get("takenDownAt"))
        );
    }

    /**
     * Recommended planners: extends isPublished with vote threshold and visibility.
     */
    public static Specification<Planner> isRecommended(int threshold) {
        return isPublished().and((root, query, cb) -> cb.and(
                cb.isFalse(root.get("hiddenFromRecommended")),
                cb.greaterThanOrEqualTo(root.get("upvotes"), threshold)
        ));
    }

    public static Specification<Planner> hasCategory(String category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Planner> titleContains(String text) {
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return (root, query, cb) -> cb.like(
                cb.lower(root.get("title")),
                cb.lower(cb.literal("%" + escaped + "%")),
                '\\'
        );
    }

    /**
     * Exact set membership using MySQL FIND_IN_SET on the raw comma-separated column.
     */
    public static Specification<Planner> hasKeyword(String keyword) {
        return (root, query, cb) -> cb.greaterThan(
                cb.function("FIND_IN_SET", Integer.class,
                        cb.literal(keyword),
                        root.get("selectedKeywords")),
                0
        );
    }

    /**
     * EXISTS subquery against PlannerContentIndex for a specific entity.
     */
    public static Specification<Planner> hasContentEntity(ContentEntityType entityType, String entityId) {
        return (root, query, cb) -> {
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<PlannerContentIndex> pci = subquery.from(PlannerContentIndex.class);
            subquery.select(cb.literal(1));
            subquery.where(cb.and(
                    cb.equal(pci.get("entityType"), entityType),
                    cb.equal(pci.get("entityId"), entityId),
                    cb.equal(pci.get("plannerId"), root.get("id"))
            ));
            return cb.exists(subquery);
        };
    }

    /**
     * Eager-fetch user to prevent N+1 queries.
     * Skips fetch on count queries to avoid Hibernate duplicate fetch issue.
     */
    public static Specification<Planner> fetchUser() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class) {
                root.fetch("user", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }
}
