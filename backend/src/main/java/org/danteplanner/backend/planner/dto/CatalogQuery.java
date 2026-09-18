package org.danteplanner.backend.planner.dto;

import org.danteplanner.backend.shared.entity.ContentEntityType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
