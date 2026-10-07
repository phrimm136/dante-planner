package org.danteplanner.backend.converter;
import org.danteplanner.backend.planner.entity.PlannerKeywords;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The frontend's selectable plan keywords (<code>PLANNER_KEYWORDS</code> in
 * <code>frontend/src/lib/constants.ts</code>) must equal {@link PlannerKeywords#VALID_KEYWORDS},
 * the converter's write/read gate, which drops any other keyword on save.
 *
 * <p><b>Proxy note:</b> the FE source of truth is read from
 * <code>static/i18n/EN/plannerKeywords.json</code>, whose keys are exactly the FE planner
 * keyword ids. A constants.ts ↔ plannerKeywords.json drift is an FE-side concern outside this
 * test's reach.</p>
 */
class KeywordParityTest {

    private static final Path FE_PLANNER_KEYWORDS =
            Path.of("..", "static", "i18n", "EN", "plannerKeywords.json");

    @Test
    void validKeywords_WhenComparedToFrontendList_AreEqual() throws IOException {
        Set<String> feKeywords = readFrontendKeywordIds();
        assertThat(PlannerKeywords.VALID_KEYWORDS)
                .as("BE VALID_KEYWORDS must equal FE plannerKeywords.json keys")
                .isEqualTo(feKeywords);
    }

    private Set<String> readFrontendKeywordIds() throws IOException {
        assertThat(Files.exists(FE_PLANNER_KEYWORDS))
                .as("FE keyword source not found at %s (test must run from the backend module dir)",
                        FE_PLANNER_KEYWORDS.toAbsolutePath())
                .isTrue();
        JsonNode root = new ObjectMapper().readTree(Files.readString(FE_PLANNER_KEYWORDS, StandardCharsets.UTF_8));
        Set<String> ids = new HashSet<>();
        root.fieldNames().forEachRemaining(ids::add);
        return ids;
    }
}
