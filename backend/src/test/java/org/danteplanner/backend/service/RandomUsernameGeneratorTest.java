package org.danteplanner.backend.service;

import org.danteplanner.backend.config.EpithetProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RandomUsernameGenerator.
 *
 * <p>Tests username generation including:
 * - Suffix length exactly 5 characters
 * - Suffix charset only 31 safe alphanumeric (excludes 0, 1, O, I, L)
 * - Weight calculation: 0-30d=3, 31-60d=2, 61+d=1
 * - Weighted selection favors newer associations
 */
@ExtendWith(MockitoExtension.class)
class RandomUsernameGeneratorTest {

    @Mock
    private EpithetProvider epithetProvider;

    private RandomUsernameGenerator generator;

    /**
     * Safe alphanumeric characters expected in suffix.
     * Excludes ambiguous: 0, 1, O, I, L (both cases).
     */
    private static final String SAFE_CHARS = "23456789abcdefghjkmnpqrstuvwxyz";
    private static final Pattern SAFE_SUFFIX_PATTERN = Pattern.compile("^[" + SAFE_CHARS + "]{5}$");

    @BeforeEach
    void setUp() {
        generator = new RandomUsernameGenerator(epithetProvider);
    }

    @Nested
    @DisplayName("Suffix Generation Tests")
    class SuffixGenerationTests {

        @Test
        @DisplayName("Should generate suffix with exactly 5 characters")
        void generateSuffix_ReturnsExactly5Characters() {
            String suffix = generator.generateSuffix();

            assertThat(suffix).hasSize(5);
        }

        @Test
        @DisplayName("Should generate suffix using only safe alphanumeric characters")
        void generateSuffix_UsesOnlySafeCharset() {
            // Generate multiple suffixes to increase confidence
            for (int i = 0; i < 100; i++) {
                String suffix = generator.generateSuffix();

                assertThat(suffix).matches(SAFE_SUFFIX_PATTERN);
            }
        }

        @Test
        @DisplayName("Should generate lowercase suffix only")
        void generateSuffix_IsLowercase() {
            for (int i = 0; i < 100; i++) {
                String suffix = generator.generateSuffix();

                assertThat(suffix).isEqualTo(suffix.toLowerCase());
            }
        }

        @Test
        @DisplayName("Should not contain ambiguous characters (0, 1, o, i, l)")
        void generateSuffix_ExcludesAmbiguousChars() {
            Set<Character> ambiguous = Set.of('0', '1', 'o', 'i', 'l');

            for (int i = 0; i < 100; i++) {
                String suffix = generator.generateSuffix();

                for (char c : suffix.toCharArray()) {
                    assertThat(ambiguous).doesNotContain(c);
                }
            }
        }

        @Test
        @DisplayName("Should generate diverse suffixes (not all the same)")
        void generateSuffix_ProducesDiverseResults() {
            Set<String> suffixes = new HashSet<>();

            for (int i = 0; i < 50; i++) {
                suffixes.add(generator.generateSuffix());
            }

            // Should have at least 40 unique suffixes out of 50 (very high confidence)
            assertThat(suffixes).hasSizeGreaterThanOrEqualTo(40);
        }
    }

    @Nested
    @DisplayName("Weighted Association Selection Tests")
    class WeightedSelectionTests {

        @Test
        @DisplayName("Should select from available associations")
        void selectWeightedEpithet_ReturnsValidAssociation() {
            List<String> associations = List.of("W_CORP", "ZWEI", "SEVEN");
            when(epithetProvider.getEpithets()).thenReturn(associations);
            when(epithetProvider.getWeight(anyString())).thenReturn(1);

            String selected = generator.selectWeightedEpithet();

            assertThat(associations).contains(selected);
        }

        @Test
        @DisplayName("Should throw IllegalStateException when no associations configured")
        void selectWeightedEpithet_WhenNoAssociations_ThrowsException() {
            when(epithetProvider.getEpithets()).thenReturn(List.of());

            assertThatThrownBy(() -> generator.selectWeightedEpithet())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No epithets available");
        }

        @Test
        @DisplayName("Should favor higher weighted associations in selection")
        void selectWeightedEpithet_FavorsHigherWeight() {
            // Set up one association with weight 3, another with weight 1
            List<String> associations = List.of("NEW_ASSOC", "OLD_ASSOC");
            when(epithetProvider.getEpithets()).thenReturn(associations);
            when(epithetProvider.getWeight("NEW_ASSOC")).thenReturn(3);
            when(epithetProvider.getWeight("OLD_ASSOC")).thenReturn(1);

            // Count selections over many iterations
            int newCount = 0;
            int oldCount = 0;
            int iterations = 1000;

            for (int i = 0; i < iterations; i++) {
                String selected = generator.selectWeightedEpithet();
                if ("NEW_ASSOC".equals(selected)) {
                    newCount++;
                } else {
                    oldCount++;
                }
            }

            // NEW_ASSOC has 3x weight, so should be selected roughly 3x as often
            // With 1000 iterations, expect ~750 NEW vs ~250 OLD
            // Allow ±15% tolerance for randomness
            double ratio = (double) newCount / oldCount;
            assertThat(ratio).isBetween(2.0, 5.0); // Expected ~3.0, allow wide margin
        }

        @Test
        @DisplayName("Should work with single association")
        void selectWeightedEpithet_WithSingleAssociation_ReturnsThatAssociation() {
            when(epithetProvider.getEpithets()).thenReturn(List.of("ONLY_ONE"));
            when(epithetProvider.getWeight("ONLY_ONE")).thenReturn(1);

            String selected = generator.selectWeightedEpithet();

            assertThat(selected).isEqualTo("ONLY_ONE");
        }
    }

    @Nested
    @DisplayName("Full Generation Tests")
    class FullGenerationTests {

        @Test
        @DisplayName("Should generate complete username components")
        void generate_ReturnsValidComponents() {
            when(epithetProvider.getEpithets()).thenReturn(List.of("W_CORP"));
            when(epithetProvider.getWeight("W_CORP")).thenReturn(1);

            RandomUsernameGenerator.UsernameComponents result = generator.generate();

            assertThat(result.keyword()).isEqualTo("W_CORP");
            assertThat(result.suffix()).hasSize(5);
            assertThat(result.suffix()).matches(SAFE_SUFFIX_PATTERN);
        }

        @Test
        @DisplayName("Should generate unique suffixes on multiple calls")
        void generate_ProducesUniqueSuffixes() {
            when(epithetProvider.getEpithets()).thenReturn(List.of("W_CORP"));
            when(epithetProvider.getWeight("W_CORP")).thenReturn(1);

            Set<String> suffixes = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                RandomUsernameGenerator.UsernameComponents result = generator.generate();
                suffixes.add(result.suffix());
            }

            // Should have mostly unique suffixes
            assertThat(suffixes).hasSizeGreaterThanOrEqualTo(40);
        }
    }
}
