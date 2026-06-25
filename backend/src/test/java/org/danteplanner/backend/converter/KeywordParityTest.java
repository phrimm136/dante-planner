package org.danteplanner.backend.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-language parity guards for the planner keyword set.
 *
 * <p>The frontend's selectable plan keywords (<code>PLANNER_KEYWORDS</code> in
 * <code>frontend/src/lib/constants.ts</code>) must stay in lockstep with the two backend
 * enforcement points discovered for this feature: {@link KeywordSetConverter#VALID_KEYWORDS}
 * (the JPA write/read gate) and the <code>selected_keywords</code> MySQL <code>SET</code>
 * column (defined by the latest Flyway migration). If any of the three drifts, an
 * end-to-end save of "all FE keywords" would silently drop a keyword at the converter
 * filter or be rejected by the SET column. These tests fail the build instead.</p>
 *
 * <p>The expensive empirical round-trip of the full set through the real SET column lives in
 * {@code MySQLIntegrationTest} (Docker-gated); H2 cannot store all members because the
 * converter column has no explicit length and defaults to VARCHAR(255) under JPA auto-DDL.</p>
 *
 * <p><b>Proxy note:</b> the FE source of truth is read from
 * <code>static/i18n/EN/plannerKeywords.json</code>, whose keys are exactly the FE planner
 * keyword ids. A constants.ts ↔ plannerKeywords.json drift is an FE-side concern outside this
 * test's reach.</p>
 */
class KeywordParityTest {

    private static final Path FE_PLANNER_KEYWORDS =
            Path.of("..", "static", "i18n", "EN", "plannerKeywords.json");

    private static final String LATEST_KEYWORD_MIGRATION =
            "db/migration/V043__add_thumb_spider_keywords.sql";

    @Test
    @DisplayName("VALID_KEYWORDS equals the FE plannerKeywords.json id set")
    void validKeywords_matchFrontendList() throws IOException {
        Set<String> feKeywords = readFrontendKeywordIds();
        assertThat(KeywordSetConverter.VALID_KEYWORDS)
                .as("BE VALID_KEYWORDS must equal FE plannerKeywords.json keys")
                .isEqualTo(feKeywords);
    }

    @Test
    @DisplayName("VALID_KEYWORDS equals the selected_keywords SET column members")
    void validKeywords_matchMigrationSetMembers() throws IOException {
        Set<String> setMembers = readMigrationSetMembers();
        assertThat(KeywordSetConverter.VALID_KEYWORDS)
                .as("BE VALID_KEYWORDS must equal the V043 selected_keywords SET members")
                .isEqualTo(setMembers);
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

    private Set<String> readMigrationSetMembers() throws IOException {
        String sql;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(LATEST_KEYWORD_MIGRATION)) {
            assertThat(in).as("migration %s not on classpath", LATEST_KEYWORD_MIGRATION).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher setBlock = Pattern.compile("SET\\s*\\((.*?)\\)", Pattern.DOTALL).matcher(sql);
        assertThat(setBlock.find()).as("no SET(...) definition in %s", LATEST_KEYWORD_MIGRATION).isTrue();
        Matcher members = Pattern.compile("'([^']+)'").matcher(setBlock.group(1));
        Set<String> result = new HashSet<>();
        while (members.find()) {
            result.add(members.group(1));
        }
        return result;
    }
}
