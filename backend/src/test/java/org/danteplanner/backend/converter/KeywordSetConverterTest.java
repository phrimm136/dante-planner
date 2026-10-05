package org.danteplanner.backend.converter;
import org.danteplanner.backend.planner.converter.KeywordSetConverter;
import org.danteplanner.backend.planner.entity.PlannerKeywords;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KeywordSetConverter.
 *
 * <p>Tests bidirectional conversion between Set&lt;String&gt; and the JSON
 * string-array storage form, including rename normalization and read totality.</p>
 */
class KeywordSetConverterTest {

    private KeywordSetConverter converter;

    @BeforeEach
    void setUp() {
        converter = new KeywordSetConverter();
    }

    @Nested
    class ToDatabaseColumn {

        @Test
        void convertToDatabaseColumn_WhenNullSet_ReturnsNull() {
            assertNull(converter.convertToDatabaseColumn(null));
        }

        @Test
        void convertToDatabaseColumn_WhenEmptySet_ReturnsNull() {
            assertNull(converter.convertToDatabaseColumn(Set.of()));
        }

        @Test
        void convertToDatabaseColumn_WhenSingleKeyword_ReturnsKeyword() {
            String result = converter.convertToDatabaseColumn(Set.of("Combustion"));
            assertEquals("[\"Combustion\"]", result);
        }

        @Test
        void convertToDatabaseColumn_WhenMultipleKeywords_ReturnsSortedArray() {
            Set<String> keywords = new HashSet<>(Set.of("Sinking", "CRIMSON", "Combustion"));
            String result = converter.convertToDatabaseColumn(keywords);
            assertEquals("[\"CRIMSON\",\"Combustion\",\"Sinking\"]", result);
        }

        @Test
        void convertToDatabaseColumn_WhenEgoGiftKeyword_ReturnsKeyword() {
            String result = converter.convertToDatabaseColumn(Set.of("9154"));
            assertEquals("[\"9154\"]", result);
        }

        @Test
        void convertToDatabaseColumn_WhenMixedKeywordTypes_ReturnsSortedArray() {
            Set<String> keywords = new HashSet<>(Set.of("CRIMSON", "9154", "Combustion"));
            String result = converter.convertToDatabaseColumn(keywords);
            assertEquals("[\"9154\",\"CRIMSON\",\"Combustion\"]", result);
        }

        @Test
        void convertToDatabaseColumn_WhenUnknownKeyword_DropsIt() {
            Set<String> keywords = new HashSet<>(Set.of("Combustion", "REMOVED_KEYWORD"));
            String result = converter.convertToDatabaseColumn(keywords);
            assertEquals("[\"Combustion\"]", result);
        }

        @Test
        void convertToDatabaseColumn_WhenAllUnknown_ReturnsNull() {
            assertNull(converter.convertToDatabaseColumn(Set.of("REMOVED_KEYWORD", "GhostKeyword")));
        }

        @Test
        void convertToDatabaseColumn_WhenRenamedKeyword_RemapsToCurrent() {
            Set<String> keywords = new HashSet<>(Set.of("AccelBullet", "ChargeLoad"));
            String result = converter.convertToDatabaseColumn(keywords);
            assertEquals("[\"9828\",\"EmergencyChargeForceField\"]", result);
        }

        @Test
        void convertToDatabaseColumn_WhenRenamedAndCurrentPresent_Deduplicates() {
            Set<String> keywords = new HashSet<>(Set.of("AccelBullet", "9828"));
            String result = converter.convertToDatabaseColumn(keywords);
            assertEquals("[\"9828\"]", result);
        }

        @Test
        void convertToDatabaseColumn_WhenAllValidKeywords_RoundTripsAll() {
            Set<String> all = new HashSet<>(PlannerKeywords.VALID_KEYWORDS);
            String result = converter.convertToDatabaseColumn(all);
            Set<String> resultSet = converter.convertToEntityAttribute(result);
            assertEquals(all, resultSet);
        }
    }

    @Nested
    class ToEntityAttribute {

        @Test
        void convertToEntityAttribute_WhenNull_ReturnsEmptySet() {
            Set<String> result = converter.convertToEntityAttribute(null);
            assertTrue(result.isEmpty());
        }

        @Test
        void convertToEntityAttribute_WhenEmptyString_ReturnsEmptySet() {
            Set<String> result = converter.convertToEntityAttribute("");
            assertTrue(result.isEmpty());
        }

        @Test
        void convertToEntityAttribute_WhenSingleKeyword_ReturnsSet() {
            Set<String> result = converter.convertToEntityAttribute("[\"Combustion\"]");
            assertEquals(Set.of("Combustion"), result);
        }

        @Test
        void convertToEntityAttribute_WhenArray_ReturnsSet() {
            Set<String> result = converter.convertToEntityAttribute("[\"CRIMSON\",\"Combustion\",\"Sinking\"]");
            assertEquals(Set.of("CRIMSON", "Combustion", "Sinking"), result);
        }

        @Test
        void convertToEntityAttribute_WhenEgoGift_ReturnsSet() {
            Set<String> result = converter.convertToEntityAttribute("[\"9154\",\"Combustion\"]");
            assertEquals(Set.of("9154", "Combustion"), result);
        }

        @Test
        void convertToEntityAttribute_WhenInvalidKeywords_FiltersThem() {
            Set<String> result = converter.convertToEntityAttribute("[\"Combustion\",\"REMOVED_KEYWORD\",\"Sinking\"]");
            assertEquals(Set.of("Combustion", "Sinking"), result);
        }

        @Test
        void convertToEntityAttribute_WhenRenamedKeywordStored_RemapsToCurrent() {
            Set<String> result = converter.convertToEntityAttribute("[\"AccelBullet\",\"Combustion\"]");
            assertEquals(Set.of("9828", "Combustion"), result);
        }

        @Test
        void convertToEntityAttribute_WhenMalformedJson_ReturnsEmptySet() {
            Set<String> result = converter.convertToEntityAttribute("Combustion,Sinking");
            assertTrue(result.isEmpty());
        }

        @Test
        void convertToEntityAttribute_WhenNonArrayJson_ReturnsEmptySet() {
            Set<String> result = converter.convertToEntityAttribute("{\"keywords\":[\"Combustion\"]}");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class RoundTrip {

        @Test
        void roundTrip_WhenWriteThenRead_PreservesKeywords() {
            Set<String> original = new HashSet<>(Set.of("CRIMSON", "9154", "Combustion", "HeishouSynergy"));
            String dbValue = converter.convertToDatabaseColumn(original);
            Set<String> restored = converter.convertToEntityAttribute(dbValue);
            assertEquals(original, restored);
        }
    }
}
