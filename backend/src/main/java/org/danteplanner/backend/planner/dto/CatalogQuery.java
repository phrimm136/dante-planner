package org.danteplanner.backend.planner.dto;

import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record CatalogQuery(
        boolean recommendedOnly,
        TextFilter category,
        TextFilter searchTerm,
        List<String> keywords,
        Map<ContentEntityType, List<String>> entityFilters) {

    public CatalogQuery {
        keywords = List.copyOf(keywords);
        entityFilters = normalize(entityFilters);
    }

    public sealed interface TextFilter {

        record Any() implements TextFilter {
        }

        record Text(String value) implements TextFilter {
        }

        static TextFilter exactly(@Nullable String supplied) {
            return supplied == null ? new Any() : new Text(supplied);
        }

        static TextFilter nonBlank(@Nullable String supplied) {
            return supplied == null || supplied.isBlank() ? new Any() : new Text(supplied.trim());
        }
    }

    private static Map<ContentEntityType, List<String>> normalize(
            Map<ContentEntityType, List<String>> filters) {
        Map<ContentEntityType, List<String>> byType = new EnumMap<>(ContentEntityType.class);
        filters.forEach((type, ids) -> {
            if (!ids.isEmpty()) {
                byType.put(type, List.copyOf(ids));
            }
        });
        return Collections.unmodifiableMap(byType);
    }
}
