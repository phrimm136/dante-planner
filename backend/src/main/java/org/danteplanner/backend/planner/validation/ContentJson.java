package org.danteplanner.backend.planner.validation;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.InjectableValues;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.gamedata.FloorRuleTable.CategoryFloors;

import java.util.Optional;

final class ContentJson {

    sealed interface Parsed permits Tree, Malformed {

        Optional<JsonNode> tree();
    }

    record Tree(JsonNode root) implements Parsed {

        @Override
        public Optional<JsonNode> tree() {
            return Optional.of(root);
        }
    }

    record Malformed(String detail) implements Parsed {

        @Override
        public Optional<JsonNode> tree() {
            return Optional.empty();
        }
    }

    private ContentJson() {
    }

    static Parsed parse(ObjectMapper objectMapper, String content) {
        try {
            return new Tree(objectMapper.readTree(content));
        } catch (JacksonException e) {
            return new Malformed(e.getMessage());
        }
    }

    static PlannerContent bind(ObjectMapper objectMapper, JsonNode admitted, CategoryFloors layout) {
        try {
            return objectMapper.readerFor(PlannerContent.class)
                    .with(new InjectableValues.Std().addValue(CategoryFloors.class, layout))
                    .readValue(admitted);
        } catch (JacksonException e) {
            throw new IllegalStateException("content the schema admitted did not bind", e);
        }
    }
}
