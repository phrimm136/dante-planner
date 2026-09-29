package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        } catch (JsonProcessingException e) {
            return new Malformed(e.getMessage());
        }
    }
}
