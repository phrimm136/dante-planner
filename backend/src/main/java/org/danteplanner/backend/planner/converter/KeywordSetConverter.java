package org.danteplanner.backend.planner.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.danteplanner.backend.planner.entity.PlannerKeywords;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

/**
 * JPA AttributeConverter for the {@code selected_keywords} JSON column: a thin
 * format adapter between {@code Set<String>} and a JSON string array. Keyword
 * validation and rename normalization are {@link PlannerKeywords}' rules —
 * this class only serializes, deserializes, and surfaces drops in logs.
 * Reading is total: malformed storage yields an empty set.
 */
@Slf4j
@Converter
public class KeywordSetConverter implements AttributeConverter<Set<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Set<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        PlannerKeywords keywords = PlannerKeywords.fromClient(attribute);
        if (!keywords.dropped().isEmpty()) {
            // Persistence-layer cannot reject (would fail the sync), so surface the drop
            // in logs — an unknown here means an unmigrated/unknown id slipped past the client.
            log.warn("Dropping unknown planner keywords on write: {}", keywords.dropped());
        }
        if (keywords.isEmpty()) {
            return null;
        }
        List<String> sorted = keywords.asSet().stream().sorted().toList();
        try {
            return MAPPER.writeValueAsString(sorted);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize planner keywords {}", sorted, e);
            return null;
        }
    }

    @Override
    public Set<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new HashSet<>();
        }
        List<String> parsed;
        try {
            parsed = MAPPER.readValue(dbData, STRING_LIST);
        } catch (JsonProcessingException e) {
            log.warn("Unreadable planner keyword storage, treating as empty: {}", dbData, e);
            return new HashSet<>();
        }
        return PlannerKeywords.fromStorage(parsed).asSet();
    }
}
