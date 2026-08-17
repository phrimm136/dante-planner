package org.danteplanner.backend.planner.dto;

import org.danteplanner.backend.shared.entity.ContentEntityType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The filter set of one catalog search, AND-composed across every dimension it carries.
 *
 * <p>Content-entity filters are keyed by {@link ContentEntityType} rather than named one field per
 * type, so the read side never enumerates the dimensions: a new one is an enum constant and the
 * request parameter that feeds it.</p>
 *
 * <p>An absent dimension normalizes to empty — a null keyword list, a null map, and a null or empty
 * id list inside the map all mean "no predicate for that dimension". Entity filters iterate in
 * {@link ContentEntityType} declaration order regardless of the map passed in.</p>
 *
 * @param recommendedOnly restrict the result to the recommended subset
 * @param category        category filter, null for all categories
 * @param searchTerm      title/keyword search term, null or blank to skip
 * @param keywords        keyword names, each AND-composed via EXISTS
 * @param entityFilters   content-entity ids per type, each id AND-composed via EXISTS
 */
public record CatalogQuery(
        boolean recommendedOnly,
        String category,
        String searchTerm,
        List<String> keywords,
        Map<ContentEntityType, List<String>> entityFilters) {

    public CatalogQuery {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        entityFilters = normalize(entityFilters);
    }

    private static Map<ContentEntityType, List<String>> normalize(
            Map<ContentEntityType, List<String>> filters) {
        if (filters == null || filters.isEmpty()) {
            return Map.of();
        }
        Map<ContentEntityType, List<String>> byType = new EnumMap<>(ContentEntityType.class);
        filters.forEach((type, ids) -> {
            if (ids != null && !ids.isEmpty()) {
                byType.put(type, List.copyOf(ids));
            }
        });
        return Collections.unmodifiableMap(byType);
    }
}
