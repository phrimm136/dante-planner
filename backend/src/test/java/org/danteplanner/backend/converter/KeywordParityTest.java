package org.danteplanner.backend.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    private static final Path MIGRATION_DIR =
            Path.of("src", "main", "resources", "db", "migration");

    private static final Pattern MIGRATION_VERSION = Pattern.compile("^V(\\d+)__");

    private static final Pattern KEYWORD_SET_DEF =
            Pattern.compile("MODIFY COLUMN selected_keywords SET\\s*\\((.*?)\\)", Pattern.DOTALL);

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
                .as("BE VALID_KEYWORDS must equal the latest migration's selected_keywords SET members")
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
        Path migration = latestKeywordMigration();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        // A rename migration redefines the column twice (add new member, then remove the old);
        // the resulting members are the LAST MODIFY ... SET(...). Append migrations have just one.
        Matcher setBlock = KEYWORD_SET_DEF.matcher(sql);
        String lastSetBody = null;
        while (setBlock.find()) {
            lastSetBody = setBlock.group(1);
        }
        assertThat(lastSetBody).as("no SET(...) definition in %s", migration).isNotNull();
        Matcher members = Pattern.compile("'([^']+)'").matcher(lastSetBody);
        Set<String> result = new HashSet<>();
        while (members.find()) {
            result.add(members.group(1));
        }
        return result;
    }

    /**
     * Resolves the highest-versioned Flyway migration that (re)defines the
     * {@code selected_keywords} SET column. Computed rather than hardcoded so adding a keyword
     * migration needs no edit here; filtered to keyword migrations so an unrelated later
     * migration (e.g. a notification-column change) does not become the wrong target.
     */
    private Path latestKeywordMigration() throws IOException {
        assertThat(Files.isDirectory(MIGRATION_DIR))
                .as("migration dir not found at %s (test must run from the backend module dir)",
                        MIGRATION_DIR.toAbsolutePath())
                .isTrue();
        Path latest = null;
        int latestVersion = -1;
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Matcher version = MIGRATION_VERSION.matcher(file.getFileName().toString());
                if (!version.find()) {
                    continue;
                }
                int v = Integer.parseInt(version.group(1));
                if (v <= latestVersion) {
                    continue;
                }
                if (KEYWORD_SET_DEF.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
                    latest = file;
                    latestVersion = v;
                }
            }
        }
        assertThat(latest).as("no migration defines a selected_keywords SET in %s", MIGRATION_DIR).isNotNull();
        return latest;
    }
}
