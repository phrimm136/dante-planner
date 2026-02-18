package org.danteplanner.backend.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import org.danteplanner.backend.converter.KeywordSetConverter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KeywordSetConverter.
 *
 * <p>Tests bidirectional conversion between Set&lt;String&gt; and
 * comma-separated String for MySQL SET column type.</p>
 */
class KeywordSetConverterTest {

    private KeywordSetConverter converter;

    @BeforeEach
    void setUp() {
        converter = new KeywordSetConverter();
    }

    @Nested
    @DisplayName("convertToDatabaseColumn")
    class ToDatabaseColumn {

        @Test
        @DisplayName("null set returns null")
        void nullSetReturnsNull() {
            assertNull(converter.convertToDatabaseColumn(null));
        }

        @Test
        @DisplayName("empty set returns null")
        void emptySetReturnsNull() {
            assertNull(converter.convertToDatabaseColumn(Set.of()));
        }

        @Test
        @DisplayName("single valid keyword")
        void singleKeyword() {
            String result = converter.convertToDatabaseColumn(Set.of("Combustion"));
            assertEquals("Combustion", result);
        }

        @Test
        @DisplayName("multiple keywords are sorted alphabetically")
        void multipleKeywordsSorted() {
            Set<String> keywords = new HashSet<>(Set.of("Sinking", "CRIMSON", "Combustion"));
            String result = converter.convertToDatabaseColumn(keywords);
            assertEquals("CRIMSON,Combustion,Sinking", result);
        }

        @Test
        @DisplayName("ego gift keyword '9154' is accepted")
        void egoGiftKeywordAccepted() {
            String result = converter.convertToDatabaseColumn(Set.of("9154"));
            assertEquals("9154", result);
        }

        @Test
        @DisplayName("mixed keyword types including ego gift")
        void mixedKeywordTypes() {
            Set<String> keywords = new HashSet<>(Set.of("CRIMSON", "9154", "Combustion"));
            String result = converter.convertToDatabaseColumn(keywords);
            assertEquals("9154,CRIMSON,Combustion", result);
        }

        @Test
        @DisplayName("invalid keyword throws IllegalArgumentException")
        void invalidKeywordThrows() {
            Set<String> keywords = Set.of("Combustion", "InvalidKeyword");
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> converter.convertToDatabaseColumn(keywords)
            );
            assertTrue(ex.getMessage().contains("InvalidKeyword"));
        }

        @Test
        @DisplayName("all valid keywords are accepted")
        void allValidKeywordsAccepted() {
            Set<String> all = new HashSet<>(KeywordSetConverter.VALID_KEYWORDS);
            String result = converter.convertToDatabaseColumn(all);
            Set<String> resultSet = converter.convertToEntityAttribute(result);
            assertEquals(all, resultSet);
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute")
    class ToEntityAttribute {

        @Test
        @DisplayName("null string returns empty set")
        void nullReturnsEmpty() {
            Set<String> result = converter.convertToEntityAttribute(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("empty string returns empty set")
        void emptyReturnsEmpty() {
            Set<String> result = converter.convertToEntityAttribute("");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("single keyword parsed")
        void singleKeyword() {
            Set<String> result = converter.convertToEntityAttribute("Combustion");
            assertEquals(Set.of("Combustion"), result);
        }

        @Test
        @DisplayName("comma-separated keywords parsed")
        void commaSeparated() {
            Set<String> result = converter.convertToEntityAttribute("CRIMSON,Combustion,Sinking");
            assertEquals(Set.of("CRIMSON", "Combustion", "Sinking"), result);
        }

        @Test
        @DisplayName("ego gift '9154' parsed from DB")
        void egoGiftParsed() {
            Set<String> result = converter.convertToEntityAttribute("9154,Combustion");
            assertEquals(Set.of("9154", "Combustion"), result);
        }

        @Test
        @DisplayName("invalid keywords silently filtered out")
        void invalidKeywordsFiltered() {
            Set<String> result = converter.convertToEntityAttribute("Combustion,REMOVED_KEYWORD,Sinking");
            assertEquals(Set.of("Combustion", "Sinking"), result);
        }

        @Test
        @DisplayName("whitespace around keywords is trimmed")
        void whitespaceTrimmed() {
            Set<String> result = converter.convertToEntityAttribute(" Combustion , Sinking ");
            assertEquals(Set.of("Combustion", "Sinking"), result);
        }
    }

    @Nested
    @DisplayName("Round-trip consistency")
    class RoundTrip {

        @Test
        @DisplayName("write then read preserves keywords")
        void writeReadPreserves() {
            Set<String> original = new HashSet<>(Set.of("CRIMSON", "9154", "Combustion", "HeishouSynergy"));
            String dbValue = converter.convertToDatabaseColumn(original);
            Set<String> restored = converter.convertToEntityAttribute(dbValue);
            assertEquals(original, restored);
        }
    }
}
