package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.gamedata.FloorRuleTable;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.gamedata.PlannerIdMigrations;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.planner.validation.floor.Stage;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PlannerContentValidator.
 *
 * <p>Tests all validation rules for planner content including:
 * required fields, field types, sinner indices,
 * content size limits, note size limits, and unknown fields detection.</p>
 *
 * <p>Note: title is in metadata, category is passed as a parameter.</p>
 *
 * <p>PlannerContent structure from frontend (all 11 fields required):
 * - selectedKeywords: string[]
 * - selectedBuffIds: number[]
 * - selectedGiftKeyword: string | null
 * - selectedGiftIds: string[]
 * - observationGiftIds: string[]
 * - comprehensiveGiftIds: string[]
 * - equipment: Record&lt;string, SinnerEquipment&gt; (keys are "1"-"12")
 * - deploymentOrder: number[] (values 0-11)
 * - skillEAState: Record&lt;string, SkillEAState&gt;
 * - floorSelections: SerializableFloorSelection[]
 * - sectionNotes: Record&lt;string, SerializableNoteContent&gt;
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PlannerContentValidatorTest {

    private PlannerContentValidator validator;
    private ObjectMapper objectMapper;

    @Mock
    private GameDataRegistry gameDataRegistry;

    @Mock
    private SinnerIdValidator sinnerIdValidator;

    // Default size limits matching application.properties (50KB content, 1KB notes)
    private static final int MAX_CONTENT_SIZE_BYTES = 51200;
    private static final int MAX_NOTE_SIZE_BYTES = 1024;

    private static final int VERSION = 7;

    private static final FloorRuleTable FLOOR_RULE_TABLE = new GameDataLoader(new ObjectMapper())
            .loadFloorRules(Path.of("../static/data", GameDataRegistry.FLOOR_RULES_FILE));

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient().when(gameDataRegistry.floorRules()).thenReturn(FLOOR_RULE_TABLE);
        validator = new PlannerContentValidator(objectMapper, MAX_CONTENT_SIZE_BYTES, MAX_NOTE_SIZE_BYTES,
                gameDataRegistry, sinnerIdValidator, new FloorRules(gameDataRegistry));

        // Default per-EGO max so existing tests reach gift/buff/etc. validations
        // without stubbing maxThreadspin individually. Tests that exercise the
        // per-EGO threadspin rule override this with a specific stub.
        lenient().when(gameDataRegistry.getEgoMaxThreadspin(anyString())).thenReturn(4);
        lenient().when(gameDataRegistry.idMigrations()).thenReturn(PlannerIdMigrations.EMPTY);
        lenient().when(gameDataRegistry.hasSeason(VERSION)).thenReturn(true);
    }

    private String validate(String content, String category) {
        return validate(content, category, Stage.DRAFT);
    }

    private String validate(String content, String category, Stage stage) {
        return validator.parse(content, MDCategory.fromValue(category), validator.season(VERSION), stage).json();
    }

    // ========================================================================
    // Mock Setup Helpers
    // ========================================================================

    /**
     * Setup base mocks for equipment/floor validation (identity, ego, gift, themePack, sinnerMatch).
     */
    private void setupBaseMocks() {
        when(gameDataRegistry.hasIdentity(anyString())).thenReturn(true);
        when(gameDataRegistry.hasEgo(anyString())).thenReturn(true);
        when(gameDataRegistry.hasEgoGift(anyString())).thenReturn(true);
        when(gameDataRegistry.hasThemePack(anyString())).thenReturn(true);
        lenient().when(gameDataRegistry.isGiftAffordableForThemePack(anyString(), anyString())).thenReturn(true);
        when(sinnerIdValidator.validateMatch(anyString(), anyString())).thenReturn(true);
    }

    /**
     * Setup mocks for start buff validation.
     */
    private void setupStartBuffMocks() {
        when(gameDataRegistry.hasStartBuff(anyInt(), anyString())).thenReturn(true);
    }

    /**
     * Setup mocks for start gift validation.
     * Uses Combustion pool: [9001, 9009, 9103]
     */
    private void setupStartGiftMocks() {
        when(gameDataRegistry.hasStartGiftKeyword(anyInt(), anyString())).thenReturn(true);
        when(gameDataRegistry.getStartGiftPool(anyInt(), anyString())).thenReturn(Set.of("9001", "9009", "9103"));
    }

    /**
     * Setup mock to allow all IDs as valid.
     * Call this in tests that need full validation including buffs and gift pools.
     */
    private void setupMocksForValidIds() {
        setupBaseMocks();
        setupStartBuffMocks();
        setupStartGiftMocks();
    }

    /**
     * Setup mock for tests where selectedBuffIds is empty but validation continues to gift check.
     */
    private void setupMocksForValidIdsWithoutBuffs() {
        setupBaseMocks();
        setupStartGiftMocks();
    }

    /**
     * Setup mock for tests where validation fails before gift check (in buff validation).
     */
    private void setupMocksForValidIdsWithoutGifts() {
        setupBaseMocks();
        setupStartBuffMocks();
    }

    /**
     * Setup mock for tests where validation fails before both buff and gift checks.
     */
    private void setupMocksForValidIdsWithoutBuffsAndGifts() {
        setupBaseMocks();
    }

    /**
     * Setup mocks for tests where gift ID validation fails due to duplicate check.
     * Includes hasEgoGift since first ID is validated before duplicate detected.
     */
    private void setupMocksForGiftDuplicateFailure() {
        when(gameDataRegistry.hasIdentity(anyString())).thenReturn(true);
        when(gameDataRegistry.hasEgo(anyString())).thenReturn(true);
        when(gameDataRegistry.hasEgoGift(anyString())).thenReturn(true);
        when(sinnerIdValidator.validateMatch(anyString(), anyString())).thenReturn(true);
    }


    /**
     * Setup mocks for tests where floor gift affordability fails.
     * All other IDs are valid; only isGiftAffordableForThemePack returns false.
     */
    private void setupMocksForUnaffordableGifts() {
        when(gameDataRegistry.hasIdentity(anyString())).thenReturn(true);
        when(gameDataRegistry.hasEgo(anyString())).thenReturn(true);
        when(gameDataRegistry.hasEgoGift(anyString())).thenReturn(true);
        when(gameDataRegistry.hasThemePack(anyString())).thenReturn(true);
        when(gameDataRegistry.isGiftAffordableForThemePack(anyString(), anyString())).thenReturn(false);
        when(gameDataRegistry.hasStartBuff(anyInt(), anyString())).thenReturn(true);
        when(gameDataRegistry.hasStartGiftKeyword(anyInt(), anyString())).thenReturn(true);
        when(gameDataRegistry.getStartGiftPool(anyInt(), anyString())).thenReturn(Set.of("9001", "9009", "9103"));
        when(sinnerIdValidator.validateMatch(anyString(), anyString())).thenReturn(true);
    }

    /**
     * Creates minimal valid planner content with all required fields.
     * Equipment keys are the sinner numbers "1" to "12".
     * EGOs are keyed by type (e.g., "ZAYIN").
     */
    private String createValidContent() {
        return """
            {
                "selectedKeywords": ["Combustion", "Slash"],
                "equipment": {
                    "1": {
                        "identity": {"id": "10101", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20101", "threadspin": 4}}
                    },
                    "2": {
                        "identity": {"id": "10201", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20201", "threadspin": 4}}
                    },
                    "3": {
                        "identity": {"id": "10301", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20301", "threadspin": 4}}
                    },
                    "4": {
                        "identity": {"id": "10401", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20401", "threadspin": 4}}
                    },
                    "5": {
                        "identity": {"id": "10501", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20501", "threadspin": 4}}
                    },
                    "6": {
                        "identity": {"id": "10601", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20601", "threadspin": 4}}
                    },
                    "7": {
                        "identity": {"id": "10701", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20701", "threadspin": 4}}
                    },
                    "8": {
                        "identity": {"id": "10801", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20801", "threadspin": 4}}
                    },
                    "9": {
                        "identity": {"id": "10901", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20901", "threadspin": 4}}
                    },
                    "10": {
                        "identity": {"id": "11001", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "21001", "threadspin": 4}}
                    },
                    "11": {
                        "identity": {"id": "11101", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "21101", "threadspin": 4}}
                    },
                    "12": {
                        "identity": {"id": "11201", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "21201", "threadspin": 4}}
                    }
                },
                "deploymentOrder": [0, 1, 2, 3, 4, 5],
                "skillEAState": {
                    "1": {"0": 3, "1": 2, "2": 1},
                    "2": {"0": 3, "1": 2, "2": 1},
                    "3": {"0": 3, "1": 2, "2": 1},
                    "4": {"0": 3, "1": 2, "2": 1},
                    "5": {"0": 3, "1": 2, "2": 1},
                    "6": {"0": 3, "1": 2, "2": 1},
                    "7": {"0": 3, "1": 2, "2": 1},
                    "8": {"0": 3, "1": 2, "2": 1},
                    "9": {"0": 3, "1": 2, "2": 1},
                    "10": {"0": 3, "1": 2, "2": 1},
                    "11": {"0": 3, "1": 2, "2": 1},
                    "12": {"0": 3, "1": 2, "2": 1}
                },
                "selectedBuffIds": [100, 201],
                "selectedGiftKeyword": "Combustion",
                "selectedGiftIds": ["9001"],
                "observationGiftIds": [],
                "comprehensiveGiftIds": [],
                "floorSelections": [
                    {"themePackId": "1001", "difficulty": 0, "giftIds": ["9002"]},
                    {"themePackId": "1002", "difficulty": 0, "giftIds": []},
                    {"themePackId": "1003", "difficulty": 0, "giftIds": []},
                    {"themePackId": "1004", "difficulty": 0, "giftIds": []},
                    {"themePackId": "1005", "difficulty": 0, "giftIds": []}
                ],
                "sectionNotes": {}
            }
            """;
    }

    /**
     * Creates valid planner content with all optional fields included.
     * Equipment keys are the sinner numbers "1" to "12".
     * Each sinner has identity + ZAYIN ego.
     * skillEAState: each sinner's skill slots sum to 6 (3+2+1).
     */
    private String createFullContent() {
        return """
            {
                "selectedKeywords": ["Combustion"],
                "selectedBuffIds": [100, 201, 302],
                "selectedGiftKeyword": "Combustion",
                "selectedGiftIds": ["9001", "9009"],
                "observationGiftIds": ["9100"],
                "comprehensiveGiftIds": ["19050"],
                "equipment": {
                    "1": {"identity": {"id": "10101", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20101", "threadspin": 4}}},
                    "2": {"identity": {"id": "10201", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20201", "threadspin": 4}}},
                    "3": {"identity": {"id": "10301", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20301", "threadspin": 4}}},
                    "4": {"identity": {"id": "10401", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20401", "threadspin": 4}}},
                    "5": {"identity": {"id": "10501", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20501", "threadspin": 4}}},
                    "6": {"identity": {"id": "10601", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20601", "threadspin": 4}}},
                    "7": {"identity": {"id": "10701", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20701", "threadspin": 4}}},
                    "8": {"identity": {"id": "10801", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20801", "threadspin": 4}}},
                    "9": {"identity": {"id": "10901", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20901", "threadspin": 4}}},
                    "10": {"identity": {"id": "11001", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "21001", "threadspin": 4}}},
                    "11": {"identity": {"id": "11101", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "21101", "threadspin": 4}}},
                    "12": {"identity": {"id": "11201", "uptie": 3, "level": 40}, "egos": {"ZAYIN": {"id": "21201", "threadspin": 4}}}
                },
                "deploymentOrder": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
                "skillEAState": {
                    "1": {"0": 3, "1": 2, "2": 1},
                    "2": {"0": 3, "1": 2, "2": 1},
                    "3": {"0": 3, "1": 2, "2": 1},
                    "4": {"0": 3, "1": 2, "2": 1},
                    "5": {"0": 3, "1": 2, "2": 1},
                    "6": {"0": 3, "1": 2, "2": 1},
                    "7": {"0": 3, "1": 2, "2": 1},
                    "8": {"0": 3, "1": 2, "2": 1},
                    "9": {"0": 3, "1": 2, "2": 1},
                    "10": {"0": 3, "1": 2, "2": 1},
                    "11": {"0": 3, "1": 2, "2": 1},
                    "12": {"0": 3, "1": 2, "2": 1}
                },
                "floorSelections": [
                    {"themePackId": "1001", "difficulty": 0, "giftIds": ["9003"]}
                ],
                "sectionNotes": {
                    "floor-1": {"content": {"type": "doc", "content": []}}
                }
            }
            """;
    }

    @Nested
    @DisplayName("Valid Content Tests")
    class ValidContentTests {

        @Test
        @DisplayName("Should pass validation with all required fields")
        void validate_WhenAllRequiredFields_Passes() {
            setupMocksForValidIds();
            String content = createValidContent();
            String result = assertDoesNotThrow(() -> validate(content, "5F"));
            assertEquals(content, result);
        }

        @Test
        @DisplayName("Should pass validation with required and optional fields")
        void validate_WhenRequiredAndOptionalFields_Passes() {
            setupMocksForValidIds();
            String content = createFullContent();
            String result = assertDoesNotThrow(() -> validate(content, "5F"));
            assertEquals(content, result);
        }

        @Test
        @DisplayName("Should pass validation with null selectedGiftKeyword")
        void validate_WhenNullSelectedGiftKeyword_Passes() {
            setupMocksForValidIds();
            // Add selectedGiftKeyword: null to valid content
            String content = createValidContent().replace(
                    "\"selectedKeywords\": [\"Combustion\", \"Slash\"]",
                    "\"selectedKeywords\": [\"Combustion\", \"Slash\"],\n                \"selectedGiftKeyword\": null"
            );

            assertDoesNotThrow(() -> validate(content, "5F"));
        }

        @ParameterizedTest
        @EnumSource(MDCategory.class)
        void validate_WhenCategoryIsValid_Passes(MDCategory category) {
            setupMocksForValidIds();

            assertThatCode(() -> validate(createValidContent(), category.getValue()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw exception when equipment is empty (missing all 12 sinners)")
        void validate_WhenEmptyEquipment_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should pass with valid equipment and empty deploymentOrder array")
        void validate_WhenEmptyDeploymentOrder_Passes() {
            setupMocksForValidIds();
            // Uses createValidContent() which has complete equipment (12 sinners)
            // but with empty deploymentOrder
            String content = createValidContent().replace(
                    "\"deploymentOrder\": [0, 1, 2, 3, 4, 5]",
                    "\"deploymentOrder\": []"
            );

            assertDoesNotThrow(() -> validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Missing Required Fields Tests")
    class MissingRequiredFieldsTests {

        @Test
        @DisplayName("Should throw exception when selectedKeywords is missing")
        void validate_WhenMissingSelectedKeywords_ThrowsException() {
            String content = """
                {
                    "equipment": {},
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when equipment is missing")
        void validate_WhenMissingEquipment_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when deploymentOrder is missing")
        void validate_WhenMissingDeploymentOrder_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when floorSelections is missing")
        void validate_WhenMissingFloorSelections_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when sectionNotes is missing")
        void validate_WhenMissingSectionNotes_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [],
                    "floorSelections": []
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Wrong Field Types Tests")
    class WrongFieldTypesTests {

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource(delimiter = '|', value = {
            "selectedKeywords    | \"not-array\"",
            "equipment           | []",
            "deploymentOrder     | \"not-array\"",
            "floorSelections     | {}",
            "sectionNotes        | []",
            "selectedGiftKeyword | 123",
            "selectedBuffIds     | \"not-array\"",
            "skillEAState        | []",
        })
        void validate_WhenFieldHasWrongType_ThrowsException(String field, String wrongTypedValue) {
            String content = contentWithWrongType(field, wrongTypedValue);

            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        /**
         * The five required fields at their valid empty values, with {@code field} carrying
         * {@code value} instead — replacing a required field, or adding an optional one.
         */
        private String contentWithWrongType(String field, String value) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("selectedKeywords", "[]");
            fields.put("equipment", "{}");
            fields.put("deploymentOrder", "[]");
            fields.put("floorSelections", "[]");
            fields.put("sectionNotes", "{}");
            fields.put(field, value);
            return fields.entrySet().stream()
                    .map(entry -> "\"" + entry.getKey() + "\": " + entry.getValue())
                    .collect(Collectors.joining(", ", "{", "}"));
        }
    }

    @Nested
    @DisplayName("Unknown Fields Tests (STRICT mode)")
    class UnknownFieldsTests {

        @Test
        @DisplayName("Should throw exception for unknown field")
        void validate_WhenUnknownField_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {},
                    "unknownField": "value"
                }
                """;

            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> validate(content, "5F")
            );

            assertEquals("VALIDATION_ERROR", exception.getOriginalCode());
            assertThat(exception.getSubErrors()).contains(new ValidationError("UNKNOWN_FIELD", "unknownField",
                    "Field 'unknownField' is unknown"));
        }

        @Test
        @DisplayName("Should throw exception for multiple unknown fields")
        void validate_WhenMultipleUnknownFields_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {},
                    "unknownField1": "value",
                    "unknownField2": 123
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Content Size Tests")
    class ContentSizeTests {

        @Test
        @DisplayName("Should throw exception when content size exceeds 50KB limit")
        void validate_WhenContentExceedsSizeLimit_ThrowsException() {
            // Generate content that exceeds 50KB (51200 bytes)
            // Use title field to exceed the limit since it's a valid field
            StringBuilder sb = new StringBuilder("{\"title\":\"");
            sb.append("x".repeat(52000));
            sb.append("\",\"category\":\"5F\",\"selectedKeywords\":[],\"equipment\":{},");
            sb.append("\"deploymentOrder\":[],\"floorSelections\":[],\"sectionNotes\":{}}");

            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> validate(sb.toString(), "5F")
            );

            // Granular error code for size limit exceeded
            assertEquals("SIZE_EXCEEDED", exception.getOriginalCode());
        }

        @Test
        @DisplayName("Should pass when content is under 50KB limit")
        void validate_WhenContentUnderLimit_Passes() {
            setupMocksForValidIds();
            // Use createValidContent which is well under 50KB
            assertDoesNotThrow(() -> validate(createValidContent(), "5F"));
        }
    }

    @Nested
    @DisplayName("Note Size Tests")
    class NoteSizeTests {

        @Test
        @DisplayName("Should throw exception when single note exceeds 1KB")
        void validate_WhenNoteTooLarge_ThrowsException() {
            String content = createValidContent().replace("\"sectionNotes\": {}",
                    "\"sectionNotes\": {\"floor-1\": {\"content\": {\"type\": \"doc\", \"text\": \""
                            + "x".repeat(1100) + "\"}}}");

            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> validate(content, "5F")
            );

            assertEquals("SIZE_EXCEEDED", exception.getOriginalCode());
        }

        @Test
        void validate_WhenANoteIsOversized_RefusesWithTheNotePathAsTheOneSubError() {
            String content = createValidContent().replace("\"sectionNotes\": {}",
                    "\"sectionNotes\": {\"floor1\": {\"content\": {\"type\": \"doc\", \"text\": \""
                            + "x".repeat(MAX_NOTE_SIZE_BYTES) + "\"}}}");

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));

            assertThat(ex.getSubErrors()).singleElement().satisfies(error -> {
                assertEquals("SIZE_EXCEEDED", error.code());
                assertEquals("sectionNotes[floor1]", error.path());
            });
            String detail = ex.getSubErrors().getFirst().message();
            assertEquals(Map.of("code", "SIZE_EXCEEDED", "message", detail), ex.getBody().getProperties());
            assertEquals(detail, ex.getBody().getDetail());
        }

        @Test
        @DisplayName("Should pass when note is under 1KB limit")
        void validate_WhenNoteUnderLimit_Passes() {
            setupMocksForValidIds();
            String noteContent = "Short note";
            // Use createValidContent and add a note section
            String content = createValidContent().replace(
                    "\"sectionNotes\": {}",
                    "\"sectionNotes\": {\"floor-1\": {\"content\": {\"type\": \"doc\", \"text\": \"" + noteContent + "\"}}}"
            );

            assertDoesNotThrow(() -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should pass with empty sectionNotes")
        void validate_WhenEmptySectionNotes_Passes() {
            setupMocksForValidIds();
            // createValidContent already has empty sectionNotes
            assertDoesNotThrow(() -> validate(createValidContent(), "5F"));
        }
    }

    @Nested
    @DisplayName("Empty/Null Content Tests")
    class EmptyNullContentTests {

        @Test
        @DisplayName("Should throw exception for null content")
        void validate_WhenNullContent_ThrowsException() {
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> validate(null, "5F")
            );

            assertEquals("EMPTY_CONTENT", exception.getOriginalCode());
        }

        @Test
        @DisplayName("Should throw exception for empty content")
        void validate_WhenEmptyContent_ThrowsException() {
            assertThrows(PlannerValidationException.class, () -> validate("", "5F"));
        }

        @Test
        @DisplayName("Should throw exception for blank content")
        void validate_WhenBlankContent_ThrowsException() {
            assertThrows(PlannerValidationException.class, () -> validate("   ", "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-JSON content")
        void validate_WhenNonJsonContent_ThrowsException() {
            assertThrows(PlannerValidationException.class, () -> validate("not json", "5F"));
        }

        @Test
        @DisplayName("Should throw exception for JSON array instead of object")
        void validate_WhenJsonArrayContent_ThrowsException() {
            assertThrows(PlannerValidationException.class, () -> validate("[]", "5F"));
        }

        @Test
        @DisplayName("Should throw exception for JSON primitive instead of object")
        void validate_WhenJsonPrimitiveContent_ThrowsException() {
            assertThrows(PlannerValidationException.class, () -> validate("\"string\"", "5F"));
        }
    }

    @Nested
    @DisplayName("Sinner Index Validation Tests")
    class SinnerIndexTests {

        @Test
        @DisplayName("Should pass with valid sinner indices (01-12) in equipment keys")
        void validate_WhenValidSinnerIndicesInEquipment_Passes() {
            setupMocksForValidIds();
            assertDoesNotThrow(() -> validate(createValidContent(), "5F"));
        }

        @Test
        @DisplayName("Should throw exception for sinner index 0 in equipment (1-indexed)")
        void validate_WhenSinnerIndex0InEquipment_ThrowsException() {
            // Equipment keys are 1-indexed (1-12), so 0 is invalid
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {
                        "0": {"identity": {"id": "10101", "uptie": 4, "level": 45}, "egos": {}}
                    },
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for sinner index above 12 in equipment")
        void validate_WhenSinnerIndexAbove12InEquipment_ThrowsException() {
            // Equipment keys are 1-indexed (1-12), so 13 is invalid
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {
                        "13": {"identity": {"id": "10101", "uptie": 4, "level": 45}, "egos": {}}
                    },
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-numeric equipment key")
        void validate_WhenNonNumericEquipmentKey_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {
                        "yi_sang": {"identity": {"id": "10101", "uptie": 4, "level": 45}, "egos": {}}
                    },
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should pass with valid sinner indices (0-11) in deploymentOrder")
        void validate_WhenValidSinnerIndicesInDeploymentOrder_Passes() {
            setupMocksForValidIds();
            // Use createValidContent and replace deploymentOrder with all valid indices
            String content = createValidContent().replace(
                    "\"deploymentOrder\": [0, 1, 2, 3, 4, 5]",
                    "\"deploymentOrder\": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]"
            );

            assertDoesNotThrow(() -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for negative sinner index in deploymentOrder")
        void validate_WhenNegativeSinnerIndexInDeploymentOrder_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [-1, 0, 1],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for sinner index above 11 in deploymentOrder")
        void validate_WhenSinnerIndexAbove11InDeploymentOrder_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [0, 12],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-numeric value in deploymentOrder")
        void validate_WhenNonNumericDeploymentOrder_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [0, "abc"],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Start Buff Validation Tests")
    class StartBuffValidationTests {

        @Test
        @DisplayName("Should pass with valid start buff IDs")
        void validate_WhenValidStartBuffIds_Passes() {
            setupMocksForValidIds();
            // createFullContent has selectedBuffIds: [100, 201, 302]
            assertDoesNotThrow(() -> validate(createFullContent(), "5F"));
        }

        @Test
        @DisplayName("Should pass with empty selectedBuffIds")
        void validate_WhenEmptyStartBuffIds_Passes() {
            setupMocksForValidIdsWithoutBuffs();
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [],"
            );
            assertDoesNotThrow(() -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should pass with max 10 start buffs")
        void validate_WhenMax10StartBuffs_Passes() {
            setupMocksForValidIds();
            // All 10 base buffs with different enhancement levels
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [100, 201, 302, 103, 204, 305, 106, 207, 308, 109],"
            );
            assertDoesNotThrow(() -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when start buffs exceed 10")
        void validate_WhenExceedMax10StartBuffs_ThrowsException() {
            // Fails at max count check, never reaches buff ID or gift validation
            setupMocksForValidIdsWithoutBuffsAndGifts();
            // 11 buffs - exceeds limit
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 200],"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for duplicate base buff IDs (same buff, different enhancement)")
        void validate_WhenDuplicateBaseBuffId_ThrowsException() {
            setupMocksForValidIdsWithoutGifts();
            // 100 and 200 have the same base ID (00)
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [100, 200],"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for duplicate base buff IDs (base and ++)")
        void validate_WhenDuplicateBaseBuffIdBaseAndPlusPlus_ThrowsException() {
            setupMocksForValidIdsWithoutGifts();
            // 101 and 301 have the same base ID (01)
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [101, 301],"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-number in selectedBuffIds")
        void validate_WhenNonNumberInBuffIds_ThrowsException() {
            // Fails at type check before any ID validation
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [100, \"invalid\"],"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        void validate_WhenBuffIdIsAnIntegralValuedFraction_AcceptsItAsItsInteger() {
            setupMocksForValidIds();
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [100, 101.0],"
            );

            assertDoesNotThrow(() -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for buff ID not found in game data")
        void validate_WhenBuffIdNotInGameData_ThrowsException() {
            // Fails in buff validation, never reaches gift validation
            setupMocksForValidIdsWithoutGifts();
            when(gameDataRegistry.hasStartBuff(VERSION, "999")).thenReturn(false);

            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [100, 999],"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for invalid base ID (outside 00-09)")
        void validate_WhenInvalidBaseBuffId_ThrowsException() {
            // Fails in buff validation, never reaches gift validation
            setupMocksForValidIdsWithoutGifts();
            // Mock returns true but base ID 15 is invalid (valid: 00-09)
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [115],"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Start Gift Validation Tests")
    class StartGiftValidationTests {

        @Test
        @DisplayName("Should pass with valid keyword and gift IDs from pool")
        void validate_WhenValidKeywordAndGiftIds_Passes() {
            setupMocksForValidIds();
            // createValidContent has Combustion keyword with 9001 which is in the pool
            assertDoesNotThrow(() -> validate(createValidContent(), "5F"));
        }

        @Test
        @DisplayName("Should pass with null keyword and empty gift IDs")
        void validate_WhenNullKeywordEmptyGifts_Passes() {
            setupMocksForValidIdsWithoutGifts();
            String content = createValidContent()
                    .replace("\"selectedGiftKeyword\": \"Combustion\",", "\"selectedGiftKeyword\": null,")
                    .replace("\"selectedGiftIds\": [\"9001\"],", "\"selectedGiftIds\": [],");
            assertDoesNotThrow(() -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should pass with multiple gift IDs from same keyword pool")
        void validate_WhenMultipleGiftsFromSamePool_Passes() {
            setupMocksForValidIds();
            // Combustion pool has 9001, 9009, 9103
            String content = createValidContent().replace(
                    "\"selectedGiftIds\": [\"9001\"],",
                    "\"selectedGiftIds\": [\"9001\", \"9009\", \"9103\"],"
            );
            assertDoesNotThrow(() -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for gift IDs without keyword")
        void validate_WhenGiftIdsWithoutKeyword_ThrowsException() {
            // Fails at keyword check, never reaches pool validation
            setupMocksForValidIdsWithoutGifts();
            String content = createValidContent()
                    .replace("\"selectedGiftKeyword\": \"Combustion\",", "\"selectedGiftKeyword\": null,");
            // Still has selectedGiftIds: ["9001"]
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for invalid keyword")
        void validate_WhenInvalidKeyword_ThrowsException() {
            setupBaseMocks();
            setupStartBuffMocks();
            when(gameDataRegistry.hasStartGiftKeyword(VERSION, "InvalidKeyword")).thenReturn(false);

            String content = createValidContent().replace(
                    "\"selectedGiftKeyword\": \"Combustion\",",
                    "\"selectedGiftKeyword\": \"InvalidKeyword\","
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for gift ID not in keyword pool")
        void validate_WhenGiftIdNotInPool_ThrowsException() {
            setupBaseMocks();
            setupStartBuffMocks();
            when(gameDataRegistry.hasStartGiftKeyword(anyInt(), anyString())).thenReturn(true);
            // Pool only contains 9001, 9009, 9103 - but content has 9999
            when(gameDataRegistry.getStartGiftPool(anyInt(), anyString())).thenReturn(Set.of("9001", "9009", "9103"));

            String content = createValidContent().replace(
                    "\"selectedGiftIds\": [\"9001\"],",
                    "\"selectedGiftIds\": [\"9001\", \"9999\"],"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for duplicate gift IDs")
        void validate_WhenDuplicateGiftIds_ThrowsException() {
            // Duplicate check happens after first ID is validated
            setupMocksForGiftDuplicateFailure();
            String content = createValidContent().replace(
                    "\"selectedGiftIds\": [\"9001\"],",
                    "\"selectedGiftIds\": [\"9001\", \"9001\"],"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-string gift ID")
        void validate_WhenNonStringGiftId_ThrowsException() {
            String content = createValidContent().replace(
                    "\"selectedGiftIds\": [\"9001\"],",
                    "\"selectedGiftIds\": [9001],"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Comprehensive/Observation Gift Validation Tests")
    class ComprehensiveGiftValidationTests {

        @Test
        @DisplayName("Should pass with valid comprehensiveGiftIds")
        void validate_WhenValidComprehensiveGiftIds_Passes() {
            setupMocksForValidIds();
            String content = createFullContent();
            assertDoesNotThrow(() -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for duplicate comprehensiveGiftIds")
        void validate_WhenDuplicateComprehensiveGiftIds_ThrowsException() {
            // Duplicate check happens after first ID is validated
            setupMocksForGiftDuplicateFailure();
            String content = createFullContent().replace(
                    "\"comprehensiveGiftIds\": [\"19050\"]",
                    "\"comprehensiveGiftIds\": [\"19050\", \"19050\"]"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-string comprehensiveGiftIds element")
        void validate_WhenNonStringComprehensiveGiftId_ThrowsException() {
            String content = createFullContent().replace(
                    "\"comprehensiveGiftIds\": [\"19050\"]",
                    "\"comprehensiveGiftIds\": [19050]"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for invalid comprehensiveGiftIds")
        void validate_WhenInvalidComprehensiveGiftId_ThrowsException() {
            // ID lookup fails - need hasEgoGift mock
            setupMocksForGiftDuplicateFailure();
            when(gameDataRegistry.hasEgoGift("9999")).thenReturn(false);

            String content = createFullContent().replace(
                    "\"comprehensiveGiftIds\": [\"19050\"]",
                    "\"comprehensiveGiftIds\": [\"9999\"]"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for duplicate observationGiftIds")
        void validate_WhenDuplicateObservationGiftIds_ThrowsException() {
            // Duplicate check happens after first ID is validated
            setupMocksForGiftDuplicateFailure();
            String content = createFullContent().replace(
                    "\"observationGiftIds\": [\"9100\"]",
                    "\"observationGiftIds\": [\"9100\", \"9100\"]"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-string observationGiftIds element")
        void validate_WhenNonStringObservationGiftId_ThrowsException() {
            String content = createFullContent().replace(
                    "\"observationGiftIds\": [\"9100\"]",
                    "\"observationGiftIds\": [9100]"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Floor Selection Gift Validation Tests")
    class FloorSelectionGiftValidationTests {

        @Test
        @DisplayName("Should pass with valid floor giftIds")
        void validate_WhenValidFloorGiftIds_Passes() {
            setupMocksForValidIds();
            String content = createValidContent();
            assertDoesNotThrow(() -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for duplicate giftIds within same floor")
        void validate_WhenDuplicateFloorGiftIds_ThrowsException() {
            // Floor validation fails before buff/gift pool validation
            setupMocksForValidIdsWithoutBuffsAndGifts();
            String content = createValidContent().replace(
                    "\"giftIds\": [\"9002\"]",
                    "\"giftIds\": [\"9002\", \"9002\"]"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-string floor giftIds element")
        void validate_WhenNonStringFloorGiftId_ThrowsException() {
            String content = createValidContent().replace(
                    "\"giftIds\": [\"9002\"]",
                    "\"giftIds\": [9002]"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for invalid floor giftIds")
        void validate_WhenInvalidFloorGiftId_ThrowsException() {
            // Floor validation fails before buff/gift pool validation
            setupMocksForValidIdsWithoutBuffsAndGifts();
            when(gameDataRegistry.hasEgoGift("9999")).thenReturn(false);

            String content = createValidContent().replace(
                    "\"giftIds\": [\"9002\"]",
                    "\"giftIds\": [\"9999\"]"
            );
            assertThrows(PlannerValidationException.class, () -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should allow same giftId across different floors")
        void validate_WhenSameGiftIdAcrossFloors_Passes() {
            setupMocksForValidIds();
            String original = createValidContent();
            String floor2Selection = "{\"themePackId\": \"1002\", \"difficulty\": 0, \"giftIds\": []}";
            assertThat(original).containsOnlyOnce(floor2Selection);
            String content = original.replace(
                    floor2Selection,
                    "{\"themePackId\": \"1002\", \"difficulty\": 0, \"giftIds\": [\"9002\"]}"
            );
            assertThat(content).isNotEqualTo(original);
            assertDoesNotThrow(() -> validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Granular Error Codes")
    class GranularErrorCodeTests {

        @Test
        @DisplayName("Gift not affordable for theme pack should produce UNAFFORDABLE_EGOGIFT sub-error")
        void validate_WhenGiftNotAffordable_ProducesUnaffordableEgoGiftCode() {
            setupMocksForUnaffordableGifts();

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(createValidContent(), "5F"));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "UNAFFORDABLE_EGOGIFT".equals(e.code())),
                    "Expected UNAFFORDABLE_EGOGIFT in sub-errors");
        }

        @Test
        @DisplayName("Duplicate gift ID in floor giftIds should produce DUPLICATE_EGOGIFT sub-error")
        void validate_WhenDuplicateFloorGiftId_ProducesDuplicateEgoGiftCode() {
            setupMocksForValidIds();
            String content = createValidContent().replace(
                    "\"giftIds\": [\"9002\"]",
                    "\"giftIds\": [\"9002\", \"9002\"]"
            );

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "DUPLICATE_EGOGIFT".equals(e.code())),
                    "Expected DUPLICATE_EGOGIFT in sub-errors");
        }

        @Test
        @DisplayName("Duplicate base ID in selectedBuffIds should produce DUPLICATE_STARTBUFF sub-error")
        void validate_WhenDuplicateBuffBaseId_ProducesDuplicateStartBuffCode() {
            setupMocksForValidIds();
            // 100 and 200 share base ID 0 (100 % 100 == 0, 200 % 100 == 0)
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201]",
                    "\"selectedBuffIds\": [100, 200]"
            );

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "DUPLICATE_STARTBUFF".equals(e.code())),
                    "Expected DUPLICATE_STARTBUFF in sub-errors");
        }

        @Test
        @DisplayName("Duplicate gift ID in selectedGiftIds should produce DUPLICATE_STARTGIFT sub-error")
        void validate_WhenDuplicateSelectedGiftId_ProducesDuplicateStartGiftCode() {
            setupMocksForValidIds();
            String content = createValidContent().replace(
                    "\"selectedGiftIds\": [\"9001\"]",
                    "\"selectedGiftIds\": [\"9001\", \"9001\"]"
            );

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));

            assertThat(ex.getSubErrors()).extracting(ValidationError::code).containsOnlyOnce("DUPLICATE_STARTGIFT");
        }

        @Test
        @DisplayName("Floor N having themePackId when floor N-1 lacks it should produce OUT_OF_ORDER_THEMEPACK sub-error")
        void validate_WhenFloorPrerequisiteViolation_ProducesOutOfOrderThemePackCode() {
            setupMocksForValidIds();
            // Floor 0 has null themePackId; floor 1 has one → prerequisite violation
            String content = createValidContent().replace(
                    "{\"themePackId\": \"1001\", \"difficulty\": 0, \"giftIds\": [\"9002\"]}",
                    "{\"themePackId\": null, \"difficulty\": 0, \"giftIds\": []}, {\"themePackId\": \"1001\", \"difficulty\": 0, \"giftIds\": []}"
            );

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "OUT_OF_ORDER_THEMEPACK".equals(e.code())),
                    "Expected OUT_OF_ORDER_THEMEPACK in sub-errors");
        }

        @Test
        @DisplayName("selectedGiftIds present without selectedGiftKeyword should produce MISSING_STARTGIFT_KEYWORD sub-error")
        void validate_WhenGiftsWithoutKeyword_ProducesMissingStartGiftKeywordCode() {
            // No start gift keyword mocks needed: validateStartGiftIds returns early on null keyword
            setupMocksForValidIdsWithoutGifts();
            String content = createValidContent().replace(
                    "\"selectedGiftKeyword\": \"Combustion\"",
                    "\"selectedGiftKeyword\": null"
            );

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "MISSING_STARTGIFT_KEYWORD".equals(e.code())),
                    "Expected MISSING_STARTGIFT_KEYWORD in sub-errors");
        }
    }

    @Nested
    @DisplayName("Multi-Error Collection")
    class MultiErrorCollectionTests {

        @Test
        @DisplayName("Two independent errors should both appear in sub-errors of combined exception")
        void validate_WhenTwoIndependentErrors_CollectsBothInSubErrors() {
            setupMocksForUnaffordableGifts();
            // deploymentOrder[0] = 99 is OOB (0–11) → OUT_OF_RANGE_DEPLOYMENT
            // floor giftIds[0] is not affordable → UNAFFORDABLE_EGOGIFT
            String content = createValidContent().replace(
                    "\"deploymentOrder\": [0, 1, 2, 3, 4, 5]",
                    "\"deploymentOrder\": [99]"
            );

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));

            assertEquals("VALIDATION_ERROR", ex.getOriginalCode());
            assertTrue(ex.getSubErrors().size() >= 2, "Expected at least 2 sub-errors");
            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "OUT_OF_RANGE_DEPLOYMENT".equals(e.code())),
                    "Expected OUT_OF_RANGE_DEPLOYMENT in sub-errors");
            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "UNAFFORDABLE_EGOGIFT".equals(e.code())),
                    "Expected UNAFFORDABLE_EGOGIFT in sub-errors");
        }

        @Test
        @DisplayName("Single validation error should produce combined exception with exactly one sub-error")
        void validate_WhenSingleError_ProducesCombinedExceptionWithOneSubError() {
            setupMocksForUnaffordableGifts();

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(createValidContent(), "5F"));

            assertEquals("VALIDATION_ERROR", ex.getOriginalCode());
            assertEquals(1, ex.getSubErrors().size());
            assertEquals("UNAFFORDABLE_EGOGIFT", ex.getSubErrors().get(0).code());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should pass with boundary sinner indices (01 and 12 for equipment, 0 and 11 for deployment)")
        void validate_WhenBoundarySinnerIndices_Passes() {
            setupMocksForValidIds();
            // createValidContent already uses boundary values:
            // - Equipment keys: "1" through "12" (all 12 sinners)
            // - DeploymentOrder: includes 0 and can include 11
            String content = createValidContent().replace(
                    "\"deploymentOrder\": [0, 1, 2, 3, 4, 5]",
                    "\"deploymentOrder\": [0, 11]"  // Boundary values 0 and 11
            );

            assertDoesNotThrow(() -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for malformed JSON")
        void validate_WhenMalformedJson_ThrowsException() {
            assertThrows(PlannerValidationException.class,
                    () -> validate("{\"title\": \"unclosed string}", "5F"));
        }

        @Test
        @DisplayName("Should handle deeply nested but valid structure")
        void validate_WhenValidDeepNesting_Passes() {
            setupMocksForValidIds();
            // Use createFullContent which has all optional fields and complete equipment
            assertDoesNotThrow(() -> validate(createFullContent(), "5F"));
        }

        @Test
        @DisplayName("Should handle unicode characters in title")
        void validate_WhenUnicodeTitle_Passes() {
            setupMocksForValidIds();
            // Use createValidContent with unicode title
            String content = createValidContent().replace(
                    "\"title\": \"Test Planner\"",
                    "\"title\": \"유니코드 테스트 タイトル\""
            );

            assertDoesNotThrow(() -> validate(content, "5F"));
        }
    }

    @Nested
    class ThreadspinPerEgoMaxTests {

        @Test
        @DisplayName("Should pass when threadspin equals the EGO's max (5 on a 5-cap EGO)")
        void validate_WhenThreadspin5OnFiveCapEgo_Passes() {
            setupMocksForValidIds();
            lenient().when(gameDataRegistry.getEgoMaxThreadspin("20101")).thenReturn(5);

            String content = createValidContent().replace(
                    "\"egos\": {\"ZAYIN\": {\"id\": \"20101\", \"threadspin\": 4}}",
                    "\"egos\": {\"ZAYIN\": {\"id\": \"20101\", \"threadspin\": 5}}"
            );

            assertDoesNotThrow(() -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should fail when threadspin exceeds the EGO's per-EGO max")
        void validate_WhenThreadspin5OnFourCapEgo_Fails() {
            setupMocksForValidIds();

            String content = createValidContent().replace(
                    "\"egos\": {\"ZAYIN\": {\"id\": \"20101\", \"threadspin\": 4}}",
                    "\"egos\": {\"ZAYIN\": {\"id\": \"20101\", \"threadspin\": 5}}"
            );

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));
            assertTrue(ex.getMessage().contains("threadspin"),
                    "Exception message should mention threadspin: " + ex.getMessage());
        }

        @Test
        @DisplayName("Should fail when EGO id has no maxThreadspin in registry")
        void validate_WhenThreadspinWithUnknownRegistryEntry_Fails() {
            setupMocksForValidIds();
            lenient().when(gameDataRegistry.getEgoMaxThreadspin(anyString())).thenReturn(null);

            assertThrows(PlannerValidationException.class,
                    () -> validate(createValidContent(), "5F"));
        }
    }

    @Nested
    class IdReferenceAccumulationTests {

        @Test
        @DisplayName("Should report the out-of-range level and uptie on one identity together")
        void validate_WhenLevelAndUptieOutOfRange_ReportsBoth() {
            setupMocksForValidIds();

            String content = createValidContent().replace(
                    "\"identity\": {\"id\": \"10101\", \"uptie\": 4, \"level\": 45}",
                    "\"identity\": {\"id\": \"10101\", \"uptie\": 99, \"level\": 999}");

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> e.message().contains("level")),
                    "Expected the level range failure in sub-errors: " + ex.getSubErrors());
            assertTrue(ex.getSubErrors().stream().anyMatch(e -> e.message().contains("uptie")),
                    "Expected the uptie range failure in sub-errors: " + ex.getSubErrors());
        }

        @Test
        void validate_WhenLevelIsAString_RejectsItAsANonNumber() {
            String content = createValidContent().replace(
                    "\"identity\": {\"id\": \"10101\", \"uptie\": 4, \"level\": 45}",
                    "\"identity\": {\"id\": \"10101\", \"uptie\": 4, \"level\": \"abc\"}");

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));

            assertThat(ex.getSubErrors()).containsExactly(new PlannerValidationException.ValidationError(
                    "INVALID_FIELD_TYPE", "equipment[1].identity.level",
                    "Field 'equipment[1].identity.level' has an invalid value: string \"abc\""));
        }

        @Test
        void validate_WhenLevelIsAnIntegralValuedFraction_AcceptsItAsItsInteger() {
            setupMocksForValidIds();
            String content = createValidContent().replace(
                    "\"identity\": {\"id\": \"10101\", \"uptie\": 4, \"level\": 45}",
                    "\"identity\": {\"id\": \"10101\", \"uptie\": 4, \"level\": 45.0}");

            assertDoesNotThrow(() -> validate(content, "5F"));
        }

        @Test
        void validate_WhenThreadspinIsAnIntegralValuedFraction_AcceptsItAsItsInteger() {
            setupMocksForValidIds();
            String content = createValidContent().replace(
                    "\"egos\": {\"ZAYIN\": {\"id\": \"20101\", \"threadspin\": 4}}",
                    "\"egos\": {\"ZAYIN\": {\"id\": \"20101\", \"threadspin\": 4.0}}");

            assertDoesNotThrow(() -> validate(content, "5F"));
        }

        @Test
        @DisplayName("Should report an out-of-range threadspin alongside a failure on another sinner")
        void validate_WhenThreadspinAndUptieOutOfRange_ReportsBoth() {
            setupMocksForValidIds();

            String content = createValidContent()
                    .replace("\"egos\": {\"ZAYIN\": {\"id\": \"20101\", \"threadspin\": 4}}",
                            "\"egos\": {\"ZAYIN\": {\"id\": \"20101\", \"threadspin\": 99}}")
                    .replace("\"identity\": {\"id\": \"10201\", \"uptie\": 4, \"level\": 45}",
                            "\"identity\": {\"id\": \"10201\", \"uptie\": 99, \"level\": 45}");

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> e.message().contains("threadspin")),
                    "Expected the threadspin range failure in sub-errors: " + ex.getSubErrors());
            assertTrue(ex.getSubErrors().stream().anyMatch(e -> e.message().contains("uptie")),
                    "Expected the uptie range failure in sub-errors: " + ex.getSubErrors());
        }

        @Test
        @DisplayName("Should accept a draft floor carrying gifts before a theme pack is chosen")
        void validate_WhenFloorHasGiftsWithoutThemePack_DoesNotThrow() {
            setupMocksForValidIds();

            String content = createValidContent().replace(
                    "{\"themePackId\": \"1004\", \"difficulty\": 0, \"giftIds\": []}",
                    "{\"themePackId\": \"1004\", \"difficulty\": 0, \"giftIds\": []},\n"
                            + "                    {\"themePackId\": null, \"difficulty\": 0, \"giftIds\": [\"9004\"]}");

            assertDoesNotThrow(() -> validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Publish Policy Floor Tests")
    class PublishPolicyTests {

        private void setupMocksWithoutThemePack() {
            when(gameDataRegistry.hasIdentity(anyString())).thenReturn(true);
            when(gameDataRegistry.hasEgo(anyString())).thenReturn(true);
            when(gameDataRegistry.hasEgoGift(anyString())).thenReturn(true);
            when(sinnerIdValidator.validateMatch(anyString(), anyString())).thenReturn(true);
            when(gameDataRegistry.hasStartBuff(anyInt(), anyString())).thenReturn(true);
            when(gameDataRegistry.hasStartGiftKeyword(anyInt(), anyString())).thenReturn(true);
            when(gameDataRegistry.getStartGiftPool(anyInt(), anyString())).thenReturn(Set.of("9001", "9009", "9103"));
        }

        @Test
        @DisplayName("Should accept a fully filled in planner")
        void validate_WhenPublishPolicyAndFloorsComplete_Passes() {
            setupMocksForValidIds();

            assertDoesNotThrow(() ->
                    validate(createValidContent(), "5F", Stage.PUBLISH));
        }

        @Test
        @DisplayName("Should reject a floor without a theme pack")
        void validate_WhenPublishPolicyAndFloorHasNoThemePack_ReportsMissingThemePack() {
            setupMocksWithoutThemePack();
            String content = createValidContent().replace(
                    "{\"themePackId\": \"1001\", \"difficulty\": 0, \"giftIds\": [\"9002\"]}",
                    "{\"themePackId\": null, \"difficulty\": 0, \"giftIds\": []}");

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F", Stage.PUBLISH));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "MISSING_THEMEPACK".equals(e.code())
                            && "floorSelections[0] must have a theme pack selected".equals(e.message())),
                    "Expected the missing themePackId failure in sub-errors: " + ex.getSubErrors());
        }

        @Test
        @DisplayName("Should reject a floor whose theme pack is unknown to game data")
        void validate_WhenPublishPolicyAndThemePackUnknown_ReportsUnknownThemePack() {
            setupMocksWithoutThemePack();
            when(gameDataRegistry.hasThemePack(anyString())).thenReturn(false);

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(createValidContent(), "5F", Stage.PUBLISH));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "UNKNOWN_THEMEPACK".equals(e.code())
                            && "floorSelections[0].themePackId ID '1001' not found or invalid".equals(e.message())),
                    "Expected the unknown themePackId failure in sub-errors: " + ex.getSubErrors());
        }

        @Test
        @DisplayName("Should reject a 10F floor that is not on Hard")
        void validate_WhenPublishPolicyAndDifficultyBelowCategoryRule_ReportsInsufficientEntry() {
            setupMocksWithoutThemePack();
            when(gameDataRegistry.hasThemePack(anyString())).thenReturn(true);
            when(gameDataRegistry.isGiftAffordableForThemePack(anyString(), anyString())).thenReturn(true);

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(createValidContent(), "10F", Stage.PUBLISH));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "INSUFFICIENT_DIFFICULTY_ENTRY".equals(e.code())
                            && "floorSelections[0].difficulty value 0 is out of range [1-1]".equals(e.message())),
                    "Expected the difficulty range failure in sub-errors: " + ex.getSubErrors());
        }

        @Test
        void validate_WhenPublishPolicyAndDifficultyIsBeyondInt_ReportsItOutOfRange() {
            setupMocksForValidIds();
            String content = createValidContent().replace(
                    "{\"themePackId\": \"1001\", \"difficulty\": 0, \"giftIds\": [\"9002\"]}",
                    "{\"themePackId\": \"1001\", \"difficulty\": 2147483648, \"giftIds\": [\"9002\"]}");

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F", Stage.PUBLISH));

            assertThat(ex.getSubErrors()).containsExactly(new ValidationError("OUT_OF_RANGE_DIFFICULTY", "floorSelections[0].difficulty",
                    "floorSelections[0].difficulty value 2147483648 is out of range [0-1]"));
        }

        @Test
        void validate_WhenPublishPolicyAndDifficultyIsAnIntegralValuedFraction_AcceptsItAsAnInteger() {
            setupMocksForValidIds();
            String content = createValidContent().replace(
                    "{\"themePackId\": \"1001\", \"difficulty\": 0, \"giftIds\": [\"9002\"]}",
                    "{\"themePackId\": \"1001\", \"difficulty\": 0.0, \"giftIds\": [\"9002\"]}");

            assertThat(validate(content, "5F", Stage.PUBLISH)).isEqualTo(content);
        }

        @Test
        void validate_WhenPublishedFloorZeroHasAnUnknownPackAndFloorTwoRepeatsAPack_ReportsBoth() {
            setupMocksForValidIds();
            when(gameDataRegistry.hasThemePack("9999")).thenReturn(false);
            String content = createValidContent()
                    .replace("{\"themePackId\": \"1001\", \"difficulty\": 0, \"giftIds\": [\"9002\"]}",
                            "{\"themePackId\": \"9999\", \"difficulty\": 0, \"giftIds\": [\"9002\"]}")
                    .replace("{\"themePackId\": \"1003\", \"difficulty\": 0, \"giftIds\": []}",
                            "{\"themePackId\": \"1002\", \"difficulty\": 0, \"giftIds\": []}");

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F", Stage.PUBLISH));

            assertThat(ex.getSubErrors()).containsExactlyInAnyOrder(
                    new ValidationError("DUPLICATE_THEMEPACK", "floorSelections[2].themePackId",
                            "floorSelections[2].themePackId repeats theme pack '1002' from floorSelections[1]"),
                    new ValidationError("UNKNOWN_THEMEPACK", "floorSelections[0].themePackId",
                            "floorSelections[0].themePackId ID '9999' not found or invalid"));
        }

        @Test
        void validate_WhenTheThemePackIsUnknown_StillChecksTheFloorsGifts() {
            setupMocksWithoutThemePack();
            when(gameDataRegistry.hasThemePack(anyString())).thenReturn(true);
            when(gameDataRegistry.hasThemePack("8888")).thenReturn(false);
            when(gameDataRegistry.hasEgoGift("9999")).thenReturn(false);
            String content = createValidContent().replace(
                    "{\"themePackId\": \"1001\", \"difficulty\": 0, \"giftIds\": [\"9002\"]}",
                    "{\"themePackId\": \"8888\", \"difficulty\": 0, \"giftIds\": [\"9999\"]}");

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));

            assertThat(ex.getSubErrors()).containsExactly(
                    new ValidationError("UNKNOWN_EGOGIFT_FLOOR", "floorSelections[0].giftIds[0]",
                            "floorSelections[0].giftIds[0] ID '9999' not found or invalid"),
                    new ValidationError("UNKNOWN_THEMEPACK", "floorSelections[0].themePackId",
                            "floorSelections[0].themePackId ID '8888' not found or invalid"));
        }

        @Test
        void validate_WhenAFloorHoldsTwoUnaffordableGifts_ReportsOneViolationPerGift() {
            setupMocksForUnaffordableGifts();
            String content = createValidContent().replace(
                    "\"giftIds\": [\"9002\"]", "\"giftIds\": [\"9002\", \"9003\"]");

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));

            assertThat(ex.getSubErrors()).containsExactly(
                    new ValidationError("UNAFFORDABLE_EGOGIFT", "floorSelections[0].giftIds[0]", "Gift '9002' is not affordable for theme pack '1001'"),
                    new ValidationError("UNAFFORDABLE_EGOGIFT", "floorSelections[0].giftIds[1]", "Gift '9003' is not affordable for theme pack '1001'"));
        }

        @Test
        void validate_WhenADraftFloorsGiftIdsIsNotAnArray_ReportsTheFieldType() {
            String content = createValidContent().replace(
                    "\"giftIds\": [\"9002\"]", "\"giftIds\": \"9002\"");

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));

            assertThat(ex.getSubErrors()).containsExactly(new ValidationError("INVALID_FIELD_TYPE", "floorSelections[0].giftIds",
                    "Field 'floorSelections[0].giftIds' has an invalid value: string \"9002\""));
        }

        @Test
        void validate_WhenWellShapedFloorsLiePastTheCount_AcceptsAndStoresThemUntouched() {
            setupMocksForValidIds();
            String content = createValidContent().replace(
                    "{\"themePackId\": \"1005\", \"difficulty\": 0, \"giftIds\": []}",
                    "{\"themePackId\": \"1005\", \"difficulty\": 0, \"giftIds\": []}, "
                            + "{\"themePackId\": \"1001\", \"difficulty\": 9, \"giftIds\": [\"9999\", \"9999\"]}");

            assertThat(validate(content, "5F", Stage.PUBLISH)).isEqualTo(content);
        }

        @Test
        void validate_WhenFloorsPastTheCountHoldGarbage_RejectsTheirShapeAlone() {
            String content = createValidContent().replace(
                    "{\"themePackId\": \"1005\", \"difficulty\": 0, \"giftIds\": []}",
                    "{\"themePackId\": \"1005\", \"difficulty\": 0, \"giftIds\": []}, 7, "
                            + "{\"themePackId\": 1001, \"difficulty\": \"hard\", \"giftIds\": {}}");

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F", Stage.PUBLISH));

            assertThat(ex.getSubErrors()).extracting(ValidationError::code, ValidationError::path).containsExactly(
                    tuple("INVALID_FIELD_TYPE", "floorSelections[5]"),
                    tuple("INVALID_FIELD_TYPE", "floorSelections[6].difficulty"),
                    tuple("INVALID_FIELD_TYPE", "floorSelections[6].giftIds"),
                    tuple("INVALID_FIELD_TYPE", "floorSelections[6].themePackId"));
        }

        @Test
        void validate_WhenFloorSelectionsIsNotAnArray_ReportsItOnce() {
            String content = createValidContent().replaceAll(
                    "(?s)\"floorSelections\": \\[.*?\\],\\s*\"sectionNotes\"",
                    "\"floorSelections\": 5, \"sectionNotes\"");

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "5F"));

            assertThat(ex.getSubErrors()).containsExactly(new ValidationError("INVALID_FIELD_TYPE", "floorSelections",
                    "Field 'floorSelections' has an invalid value: number 5"));
        }

        @Test
        void validate_WhenFloorsFiveAndTenBothViolateAtPublish_ReportsThemInFloorOrder() {
            setupMocksForValidIds();
            String floors = IntStream.range(0, 15)
                    .mapToObj(index -> "{\"themePackId\": \"" + (1001 + index) + "\", \"difficulty\": "
                            + (index == 5 ? 0 : index == 10 ? 1 : index < 10 ? 1 : 3) + ", \"giftIds\": []}")
                    .collect(Collectors.joining(", ", "\"floorSelections\": [", "], \"sectionNotes\""));
            String content = createValidContent().replaceAll(
                    "(?s)\"floorSelections\": \\[.*?\\],\\s*\"sectionNotes\"", Matcher.quoteReplacement(floors));

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validate(content, "15F", Stage.PUBLISH));

            assertThat(ex.getSubErrors()).containsExactly(
                    new ValidationError("OUT_OF_RANGE_DIFFICULTY_NOT_HARD", "floorSelections[5].difficulty",
                            "floorSelections[5].difficulty value 0 is out of range [1-1]"),
                    new ValidationError("OUT_OF_RANGE_DIFFICULTY_NOT_EXTREME", "floorSelections[10].difficulty",
                            "floorSelections[10].difficulty value 1 is out of range [3-3]"));
        }

        @Test
        void validate_WhenAPackBoundGiftSitsOnAnUnknownPackOverTheRealGameData_ReportsBoth() {
            GameDataRegistry registry = new GameDataRegistry(new GameDataLoader(objectMapper), "../static/data");
            registry.init();
            PlannerContentValidator realValidator = new PlannerContentValidator(objectMapper,
                    MAX_CONTENT_SIZE_BYTES, MAX_NOTE_SIZE_BYTES,
                    registry, new SinnerIdValidator(), new FloorRules(registry));
            String content = TestDataFactory.VALID_CONTENT.replace(
                    "{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[\"9002\"]}",
                    "{\"themePackId\":\"9999\",\"difficulty\":0,\"giftIds\":[\"9212\"]}");

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> realValidator.parse(content, MDCategory.F5, realValidator.season(VERSION), Stage.PUBLISH));

            assertThat(ex.getSubErrors()).containsExactly(
                    new ValidationError("UNAFFORDABLE_EGOGIFT", "floorSelections[0].giftIds[0]", "Gift '9212' is not affordable for theme pack '9999'"),
                    new ValidationError("UNKNOWN_THEMEPACK", "floorSelections[0].themePackId",
                            "floorSelections[0].themePackId ID '9999' not found or invalid"));
        }
    }

    @Nested
    @DisplayName("Keyword membership Tests")
    class KeywordMembershipTests {

        private String selecting(String keywords) {
            return createValidContent().replace("\"selectedKeywords\": [\"Combustion\", \"Slash\"]",
                    "\"selectedKeywords\": " + keywords);
        }

        @Test
        void validate_WhenPublishPolicyAndContentSelectsAnUnknownKeyword_RefusesItAtItsIndex() {
            setupMocksForValidIds();

            PlannerValidationException ex = assertThrows(PlannerValidationException.class, () ->
                    validate(selecting("[\"Combustion\", \"NotAKeyword\"]"), "5F", Stage.PUBLISH));

            assertThat(ex.getSubErrors()).containsExactly(new ValidationError("UNKNOWN_KEYWORD",
                    "selectedKeywords[1]", "selectedKeywords ID 'NotAKeyword' not found or invalid"));
            assertEquals("UNKNOWN_KEYWORD", ex.getBody().getProperties().get("code"));
            assertEquals("Unknown keywords: [NotAKeyword]", ex.getBody().getDetail());
        }

        @Test
        void validate_WhenPublishPolicyAndContentSelectsABlankKeyword_AnswersUnknownKeywordNamingTheBlank() {
            setupMocksForValidIds();

            PlannerValidationException ex = assertThrows(PlannerValidationException.class, () ->
                    validate(selecting("[\"Sinking\", \"\"]"), "5F", Stage.PUBLISH));

            assertThat(ex.getSubErrors()).containsExactly(new ValidationError("UNKNOWN_KEYWORD",
                    "selectedKeywords[1]", "selectedKeywords ID '' not found or invalid"));
            assertEquals("UNKNOWN_KEYWORD", ex.getBody().getProperties().get("code"));
            assertEquals("Unknown keywords: [\"\"]", ex.getBody().getDetail());
        }

        @Test
        void validate_WhenDraftPolicyAndContentSelectsAnUnknownKeyword_Passes() {
            setupMocksForValidIds();

            assertDoesNotThrow(() ->
                    validate(selecting("[\"Combustion\", \"NotAKeyword\"]"), "5F", Stage.DRAFT));
        }

        @Test
        void validate_WhenPublishPolicyAndContentSelectsARenamedKeyword_Passes() {
            setupMocksForValidIds();

            assertDoesNotThrow(() ->
                    validate(selecting("[\"AccelBullet\", \"ChargeLoad\"]"), "5F", Stage.PUBLISH));
        }

        @Test
        void validate_WhenPublishPolicyAndKeywordsMixARenameAnUnknownAndABlank_ReportsEachUnknownAtItsIndex() {
            setupMocksForValidIds();

            PlannerValidationException ex = assertThrows(PlannerValidationException.class, () ->
                    validate(selecting("[\"AccelBullet\", \"Nope\", \"\"]"), "5F", Stage.PUBLISH));

            assertThat(ex.getSubErrors()).containsExactly(
                    new ValidationError("UNKNOWN_KEYWORD", "selectedKeywords[1]",
                            "selectedKeywords ID 'Nope' not found or invalid"),
                    new ValidationError("UNKNOWN_KEYWORD", "selectedKeywords[2]",
                            "selectedKeywords ID '' not found or invalid"));
        }

        @Test
        void validate_WhenKeywordsHoldANumberBesideAnUnknown_ReportsTheNumberAlone() {
            PlannerValidationException ex = assertThrows(PlannerValidationException.class, () ->
                    validate(selecting("[\"AccelBullet\", \"Nope\", 5, \"\"]"), "5F", Stage.PUBLISH));

            assertThat(ex.getSubErrors()).containsExactly(new ValidationError("INVALID_FIELD_TYPE",
                    "selectedKeywords[2]", "Field 'selectedKeywords[2]' has an invalid value: number 5"));
            assertEquals("VALIDATION_ERROR", ex.getBody().getProperties().get("code"));
        }

        @Test
        void validate_WhenDraftPolicyAndKeywordsMixARenameAnUnknownAndABlank_Passes() {
            setupMocksForValidIds();

            assertDoesNotThrow(() -> validate(selecting("[\"AccelBullet\", \"Nope\", \"\"]"), "5F", Stage.DRAFT));
        }

        @Test
        void validate_WhenPublishPolicyAndTwoKeywordsAreUnknown_AnswersUnknownKeywordListingBoth() {
            setupMocksForValidIds();

            PlannerValidationException ex = assertThrows(PlannerValidationException.class, () ->
                    validate(selecting("[\"Zilch\", \"Combustion\", \"Nope\"]"), "5F", Stage.PUBLISH));

            assertThat(ex.getSubErrors()).extracting(ValidationError::path)
                    .containsExactly("selectedKeywords[0]", "selectedKeywords[2]");
            assertEquals("UNKNOWN_KEYWORD", ex.getBody().getProperties().get("code"));
            assertEquals("Unknown keywords: [Nope, Zilch]", ex.getBody().getDetail());
        }
    }
}
