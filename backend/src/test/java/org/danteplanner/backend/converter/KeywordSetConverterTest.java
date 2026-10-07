package org.danteplanner.backend.converter;
import org.danteplanner.backend.planner.converter.KeywordSetConverter;
import org.danteplanner.backend.planner.entity.PlannerKeywords;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }

        @Test
        void convertToDatabaseColumn_WhenEmptySet_ReturnsNull() {
            assertThat(converter.convertToDatabaseColumn(Set.of())).isNull();
        }

        @Test
        void convertToDatabaseColumn_WhenSingleKeyword_ReturnsKeyword() {
            String result = converter.convertToDatabaseColumn(Set.of("Combustion"));
            assertThat(result).isEqualTo("[\"Combustion\"]");
        }

        @Test
        void convertToDatabaseColumn_WhenMultipleKeywords_ReturnsSortedArray() {
            Set<String> keywords = new HashSet<>(Set.of("Sinking", "CRIMSON", "Combustion"));
            String result = converter.convertToDatabaseColumn(keywords);
            assertThat(result).isEqualTo("[\"CRIMSON\",\"Combustion\",\"Sinking\"]");
        }

        @Test
        void convertToDatabaseColumn_WhenEgoGiftKeyword_ReturnsKeyword() {
            String result = converter.convertToDatabaseColumn(Set.of("9154"));
            assertThat(result).isEqualTo("[\"9154\"]");
        }

        @Test
        void convertToDatabaseColumn_WhenMixedKeywordTypes_ReturnsSortedArray() {
            Set<String> keywords = new HashSet<>(Set.of("CRIMSON", "9154", "Combustion"));
            String result = converter.convertToDatabaseColumn(keywords);
            assertThat(result).isEqualTo("[\"9154\",\"CRIMSON\",\"Combustion\"]");
        }

        @Test
        void convertToDatabaseColumn_WhenUnknownKeyword_DropsIt() {
            Set<String> keywords = new HashSet<>(Set.of("Combustion", "REMOVED_KEYWORD"));
            String result = converter.convertToDatabaseColumn(keywords);
            assertThat(result).isEqualTo("[\"Combustion\"]");
        }

        @Test
        void convertToDatabaseColumn_WhenAllUnknown_ReturnsNull() {
            assertThat(converter.convertToDatabaseColumn(Set.of("REMOVED_KEYWORD", "GhostKeyword"))).isNull();
        }

        @Test
        void convertToDatabaseColumn_WhenRenamedKeyword_RemapsToCurrent() {
            Set<String> keywords = new HashSet<>(Set.of("AccelBullet", "ChargeLoad"));
            String result = converter.convertToDatabaseColumn(keywords);
            assertThat(result).isEqualTo("[\"9828\",\"EmergencyChargeForceField\"]");
        }

        @Test
        void convertToDatabaseColumn_WhenRenamedAndCurrentPresent_Deduplicates() {
            Set<String> keywords = new HashSet<>(Set.of("AccelBullet", "9828"));
            String result = converter.convertToDatabaseColumn(keywords);
            assertThat(result).isEqualTo("[\"9828\"]");
        }

        @Test
        void convertToDatabaseColumn_WhenAllValidKeywords_RoundTripsAll() {
            Set<String> all = new HashSet<>(PlannerKeywords.VALID_KEYWORDS);
            String result = converter.convertToDatabaseColumn(all);
            Set<String> resultSet = converter.convertToEntityAttribute(result);
            assertThat(resultSet).isEqualTo(all);
        }
    }

    @Nested
    class ToEntityAttribute {

        @Test
        void convertToEntityAttribute_WhenNull_ReturnsEmptySet() {
            Set<String> result = converter.convertToEntityAttribute(null);
            assertThat(result).isEmpty();
        }

        @Test
        void convertToEntityAttribute_WhenEmptyString_ReturnsEmptySet() {
            Set<String> result = converter.convertToEntityAttribute("");
            assertThat(result).isEmpty();
        }

        @Test
        void convertToEntityAttribute_WhenSingleKeyword_ReturnsSet() {
            Set<String> result = converter.convertToEntityAttribute("[\"Combustion\"]");
            assertThat(result).isEqualTo(Set.of("Combustion"));
        }

        @Test
        void convertToEntityAttribute_WhenArray_ReturnsSet() {
            Set<String> result = converter.convertToEntityAttribute("[\"CRIMSON\",\"Combustion\",\"Sinking\"]");
            assertThat(result).isEqualTo(Set.of("CRIMSON", "Combustion", "Sinking"));
        }

        @Test
        void convertToEntityAttribute_WhenEgoGift_ReturnsSet() {
            Set<String> result = converter.convertToEntityAttribute("[\"9154\",\"Combustion\"]");
            assertThat(result).isEqualTo(Set.of("9154", "Combustion"));
        }

        @Test
        void convertToEntityAttribute_WhenInvalidKeywords_FiltersThem() {
            Set<String> result = converter.convertToEntityAttribute("[\"Combustion\",\"REMOVED_KEYWORD\",\"Sinking\"]");
            assertThat(result).isEqualTo(Set.of("Combustion", "Sinking"));
        }

        @Test
        void convertToEntityAttribute_WhenRenamedKeywordStored_RemapsToCurrent() {
            Set<String> result = converter.convertToEntityAttribute("[\"AccelBullet\",\"Combustion\"]");
            assertThat(result).isEqualTo(Set.of("9828", "Combustion"));
        }

        @Test
        void convertToEntityAttribute_WhenMalformedJson_ReturnsEmptySet() {
            Set<String> result = converter.convertToEntityAttribute("Combustion,Sinking");
            assertThat(result).isEmpty();
        }

        @Test
        void convertToEntityAttribute_WhenNonArrayJson_ReturnsEmptySet() {
            Set<String> result = converter.convertToEntityAttribute("{\"keywords\":[\"Combustion\"]}");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class RoundTrip {

        @Test
        void roundTrip_WhenWriteThenRead_PreservesKeywords() {
            Set<String> original = new HashSet<>(Set.of("CRIMSON", "9154", "Combustion", "HeishouSynergy"));
            String dbValue = converter.convertToDatabaseColumn(original);
            Set<String> restored = converter.convertToEntityAttribute(dbValue);
            assertThat(restored).isEqualTo(original);
        }
    }
}
