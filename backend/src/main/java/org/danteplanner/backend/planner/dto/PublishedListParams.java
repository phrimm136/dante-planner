package org.danteplanner.backend.planner.dto;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;

import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.planner.dto.CatalogQuery.TextFilter;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.shared.util.PageWindow;

public record PublishedListParams(
        @Nullable String page,
        @Nullable String size,
        @Nullable String category,
        @Nullable String q,
        @Nullable String keyword,
        @Nullable String identity,
        @Nullable String ego,
        @Nullable String gift,
        @Nullable String themePack) {

    public CatalogQuery catalogQuery(boolean recommendedOnly) {
        return new CatalogQuery(recommendedOnly, TextFilter.exactly(category), TextFilter.nonBlank(q),
                parseCsv(keyword), entityFilters());
    }

    public Pageable pageable() {
        return PageWindow.pageable(page, size);
    }

    private Map<ContentEntityType, List<String>> entityFilters() {
        Map<ContentEntityType, List<String>> filters = new EnumMap<>(ContentEntityType.class);
        filters.put(ContentEntityType.IDENTITY, parseCsv(identity));
        filters.put(ContentEntityType.EGO, parseCsv(ego));
        filters.put(ContentEntityType.EGO_GIFT, parseCsv(gift));
        filters.put(ContentEntityType.THEME_PACK, parseCsv(themePack));
        return filters;
    }

    private static List<String> parseCsv(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
