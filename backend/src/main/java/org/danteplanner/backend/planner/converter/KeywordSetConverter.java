package org.danteplanner.backend.planner.converter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.danteplanner.backend.planner.entity.PlannerKeywords;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Converter
public class KeywordSetConverter implements AttributeConverter<Set<String>, String> {

    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Set<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        PlannerKeywords keywords = PlannerKeywords.fromClient(attribute);
        if (!keywords.dropped().isEmpty()) {
            log.warn("Dropping unknown planner keywords on write: {}", keywords.dropped());
        }
        if (keywords.isEmpty()) {
            return null;
        }
        List<String> sorted = keywords.asSet().stream().sorted().toList();
        try {
            return MAPPER.writeValueAsString(sorted);
        } catch (JacksonException e) {
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
        } catch (JacksonException e) {
            log.warn("Unreadable planner keyword storage, treating as empty: {}", dbData, e);
            return new HashSet<>();
        }
        return PlannerKeywords.fromStorage(parsed).asSet();
    }
}
