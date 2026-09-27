package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonDocumentsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sameDocument_WhenNumbersDifferOnlyInRendering_IsTrue() {
        assertThat(JsonDocuments.sameDocument(tree("{\"a\":5}"), tree("{\"a\":5.0}"))).isTrue();
    }

    @Test
    void sameDocument_WhenNumbersDifferInValue_IsFalse() {
        assertThat(JsonDocuments.sameDocument(tree("{\"a\":5}"), tree("{\"a\":6}"))).isFalse();
    }

    @Test
    void sameDocument_WhenNestedKeysAreReorderedAndNumbersRerendered_IsTrue() {
        JsonNode left = tree("{\"a\":[1,{\"b\":2,\"c\":[3]}],\"d\":{\"e\":\"x\",\"f\":4}}");
        JsonNode right = tree("{\"d\":{\"f\":4.00,\"e\":\"x\"},\"a\":[1.0,{\"c\":[3],\"b\":2}]}");

        assertThat(JsonDocuments.sameDocument(left, right)).isTrue();
    }

    @Test
    void sameDocument_WhenArrayOrderDiffers_IsFalse() {
        assertThat(JsonDocuments.sameDocument(tree("[1,2]"), tree("[2,1]"))).isFalse();
    }

    @Test
    void sameDocument_WhenANumberMeetsItsStringRendering_IsFalse() {
        assertThat(JsonDocuments.sameDocument(tree("{\"a\":5}"), tree("{\"a\":\"5\"}"))).isFalse();
    }

    private JsonNode tree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(json, e);
        }
    }
}
