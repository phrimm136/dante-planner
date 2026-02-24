package org.danteplanner.backend.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.exception.PlannerValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PlannerContentValidator.
 *
 * <p>Tests all validation rules for planner content including:
 * required fields, field types, category parameter validation, sinner indices,
 * content size limits, note size limits, and unknown fields detection.</p>
 *
 * <p>Note: title is in metadata, category is passed as a parameter.</p>
 *
 * <p>PlannerContent structure from frontend:
 * - selectedKeywords: string[]
 * - selectedBuffIds: number[] (optional)
 * - selectedGiftKeyword: string | null (optional)
 * - selectedGiftIds: string[] (optional)
 * - observationGiftIds: string[] (optional)
 * - comprehensiveGiftIds: string[] (optional)
 * - equipment: Record&lt;string, SinnerEquipment&gt; (keys are "0"-"11")
 * - deploymentOrder: number[] (values 0-11)
 * - skillEAState: Record&lt;string, SkillEAState&gt; (optional)
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

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        validator = new PlannerContentValidator(
                objectMapper,
                gameDataRegistry,
                sinnerIdValidator,
                MAX_CONTENT_SIZE_BYTES,
                MAX_NOTE_SIZE_BYTES);
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
        when(gameDataRegistry.hasStartBuff(anyString())).thenReturn(true);
    }

    /**
     * Setup mocks for start gift validation.
     * Uses Combustion pool: [9001, 9009, 9103]
     */
    private void setupStartGiftMocks() {
        when(gameDataRegistry.hasStartGiftKeyword(anyString())).thenReturn(true);
        when(gameDataRegistry.getStartGiftPool(anyString())).thenReturn(Set.of("9001", "9009", "9103"));
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
     * Setup mocks for tests where gift ID validation fails due to type error.
     * Excludes hasEgoGift since type check happens before ID lookup.
     */
    private void setupMocksForGiftTypeFailure() {
        when(gameDataRegistry.hasIdentity(anyString())).thenReturn(true);
        when(gameDataRegistry.hasEgo(anyString())).thenReturn(true);
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
        when(gameDataRegistry.hasStartBuff(anyString())).thenReturn(true);
        when(gameDataRegistry.hasStartGiftKeyword(anyString())).thenReturn(true);
        when(gameDataRegistry.getStartGiftPool(anyString())).thenReturn(Set.of("9001", "9009", "9103"));
        when(sinnerIdValidator.validateMatch(anyString(), anyString())).thenReturn(true);
    }

    /**
     * Creates minimal valid planner content with all required fields.
     * Equipment keys are 2-digit 1-indexed ("01"-"12").
     * EGOs are keyed by type (e.g., "ZAYIN").
     */
    private String createValidContent() {
        return """
            {
                "selectedKeywords": ["Combustion", "Slash"],
                "equipment": {
                    "01": {
                        "identity": {"id": "10101", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20101", "threadspin": 4}}
                    },
                    "02": {
                        "identity": {"id": "10201", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20201", "threadspin": 4}}
                    },
                    "03": {
                        "identity": {"id": "10301", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20301", "threadspin": 4}}
                    },
                    "04": {
                        "identity": {"id": "10401", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20401", "threadspin": 4}}
                    },
                    "05": {
                        "identity": {"id": "10501", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20501", "threadspin": 4}}
                    },
                    "06": {
                        "identity": {"id": "10601", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20601", "threadspin": 4}}
                    },
                    "07": {
                        "identity": {"id": "10701", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20701", "threadspin": 4}}
                    },
                    "08": {
                        "identity": {"id": "10801", "uptie": 4, "level": 45},
                        "egos": {"ZAYIN": {"id": "20801", "threadspin": 4}}
                    },
                    "09": {
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
                "selectedBuffIds": [100, 201],
                "selectedGiftKeyword": "Combustion",
                "selectedGiftIds": ["9001"],
                "floorSelections": [
                    {"themePackId": "1001", "difficulty": 0, "giftIds": ["9002"]}
                ],
                "sectionNotes": {}
            }
            """;
    }

    /**
     * Creates valid planner content with all optional fields included.
     * Equipment keys are 2-digit 1-indexed ("01"-"12").
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
                    "01": {"identity": {"id": "10101", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20101", "threadspin": 4}}},
                    "02": {"identity": {"id": "10201", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20201", "threadspin": 4}}},
                    "03": {"identity": {"id": "10301", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20301", "threadspin": 4}}},
                    "04": {"identity": {"id": "10401", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20401", "threadspin": 4}}},
                    "05": {"identity": {"id": "10501", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20501", "threadspin": 4}}},
                    "06": {"identity": {"id": "10601", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20601", "threadspin": 4}}},
                    "07": {"identity": {"id": "10701", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20701", "threadspin": 4}}},
                    "08": {"identity": {"id": "10801", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20801", "threadspin": 4}}},
                    "09": {"identity": {"id": "10901", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "20901", "threadspin": 4}}},
                    "10": {"identity": {"id": "11001", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "21001", "threadspin": 4}}},
                    "11": {"identity": {"id": "11101", "uptie": 4, "level": 45}, "egos": {"ZAYIN": {"id": "21101", "threadspin": 4}}},
                    "12": {"identity": {"id": "11201", "uptie": 3, "level": 40}, "egos": {"ZAYIN": {"id": "21201", "threadspin": 4}}}
                },
                "deploymentOrder": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
                "skillEAState": {
                    "01": {"0": 3, "1": 2, "2": 1},
                    "02": {"0": 3, "1": 2, "2": 1},
                    "03": {"0": 3, "1": 2, "2": 1},
                    "04": {"0": 3, "1": 2, "2": 1},
                    "05": {"0": 3, "1": 2, "2": 1},
                    "06": {"0": 3, "1": 2, "2": 1},
                    "07": {"0": 3, "1": 2, "2": 1},
                    "08": {"0": 3, "1": 2, "2": 1},
                    "09": {"0": 3, "1": 2, "2": 1},
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
        void validate_AllRequiredFields_Passes() {
            setupMocksForValidIds();
            JsonNode result = assertDoesNotThrow(() -> validator.validate(createValidContent(), "5F"));
            assertNotNull(result);
            assertTrue(result.isObject());
        }

        @Test
        @DisplayName("Should pass validation with required and optional fields")
        void validate_RequiredAndOptionalFields_Passes() {
            setupMocksForValidIds();
            JsonNode result = assertDoesNotThrow(() -> validator.validate(createFullContent(), "5F"));
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should pass validation with null selectedGiftKeyword")
        void validate_NullSelectedGiftKeyword_Passes() {
            setupMocksForValidIds();
            // Add selectedGiftKeyword: null to valid content
            String content = createValidContent().replace(
                    "\"selectedKeywords\": [\"Combustion\", \"Slash\"]",
                    "\"selectedKeywords\": [\"Combustion\", \"Slash\"],\n                \"selectedGiftKeyword\": null"
            );

            assertDoesNotThrow(() -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should pass validation with all valid categories")
        void validate_AllValidCategories_Pass() {
            setupMocksForValidIds();
            for (String category : new String[]{"5F", "10F", "15F"}) {
                String content = createValidContent().replace(
                        "\"category\": \"5F\"",
                        "\"category\": \"" + category + "\""
                );

                assertDoesNotThrow(() -> validator.validate(content, "5F"),
                        "Category " + category + " should be valid");
            }
        }

        @Test
        @DisplayName("Should throw exception when equipment is empty (missing all 12 sinners)")
        void validate_EmptyEquipment_ThrowsException() {
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
                    () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should pass with valid equipment and empty deploymentOrder array")
        void validate_EmptyDeploymentOrder_Passes() {
            setupMocksForValidIds();
            // Uses createValidContent() which has complete equipment (12 sinners)
            // but with empty deploymentOrder
            String content = createValidContent().replace(
                    "\"deploymentOrder\": [0, 1, 2, 3, 4, 5]",
                    "\"deploymentOrder\": []"
            );

            assertDoesNotThrow(() -> validator.validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Missing Required Fields Tests")
    class MissingRequiredFieldsTests {

        @Test
        @DisplayName("Should throw exception when selectedKeywords is missing")
        void validate_MissingSelectedKeywords_ThrowsException() {
            String content = """
                {
                    "equipment": {},
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when equipment is missing")
        void validate_MissingEquipment_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when deploymentOrder is missing")
        void validate_MissingDeploymentOrder_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when floorSelections is missing")
        void validate_MissingFloorSelections_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when sectionNotes is missing")
        void validate_MissingSectionNotes_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [],
                    "floorSelections": []
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Invalid Category Parameter Tests")
    class InvalidCategoryTests {

        @Test
        @DisplayName("Should throw exception for invalid category parameter")
        void validate_InvalidCategoryParam_ThrowsException() {
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validate(createValidContent(), "20F")
            );

            assertEquals("INVALID_CATEGORY", exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw exception for lowercase category parameter")
        void validate_LowercaseCategoryParam_ThrowsException() {
            assertThrows(PlannerValidationException.class,
                    () -> validator.validate(createValidContent(), "5f"));
        }

        @Test
        @DisplayName("Should throw exception for null category parameter")
        void validate_NullCategoryParam_ThrowsException() {
            assertThrows(PlannerValidationException.class,
                    () -> validator.validate(createValidContent(), null));
        }

        @Test
        @DisplayName("Should throw exception for empty category parameter")
        void validate_EmptyCategoryParam_ThrowsException() {
            assertThrows(PlannerValidationException.class,
                    () -> validator.validate(createValidContent(), ""));
        }
    }

    @Nested
    @DisplayName("Wrong Field Types Tests")
    class WrongFieldTypesTests {

        @Test
        @DisplayName("Should throw exception when selectedKeywords is not an array")
        void validate_SelectedKeywordsNotArray_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": "not-array",
                    "equipment": {},
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when equipment is not an object")
        void validate_EquipmentNotObject_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": [],
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when deploymentOrder is not an array")
        void validate_DeploymentOrderNotArray_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": "not-array",
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when floorSelections is not an array")
        void validate_FloorSelectionsNotArray_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [],
                    "floorSelections": {},
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when sectionNotes is not an object")
        void validate_SectionNotesNotObject_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": []
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when selectedGiftKeyword is not string or null")
        void validate_SelectedGiftKeywordWrongType_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "selectedGiftKeyword": 123,
                    "equipment": {},
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when optional selectedBuffIds is not an array")
        void validate_SelectedBuffIdsNotArray_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "selectedBuffIds": "not-array",
                    "equipment": {},
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when optional skillEAState is not an object")
        void validate_SkillEAStateNotObject_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "skillEAState": [],
                    "equipment": {},
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Unknown Fields Tests (STRICT mode)")
    class UnknownFieldsTests {

        @Test
        @DisplayName("Should throw exception for unknown field")
        void validate_UnknownField_ThrowsException() {
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
                    () -> validator.validate(content, "5F")
            );

            // Granular error code for unknown fields
            assertEquals("UNKNOWN_FIELD", exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw exception for multiple unknown fields")
        void validate_MultipleUnknownFields_ThrowsException() {
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

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Content Size Tests")
    class ContentSizeTests {

        @Test
        @DisplayName("Should throw exception when content size exceeds 50KB limit")
        void validate_ContentExceedsSizeLimit_ThrowsException() {
            // Generate content that exceeds 50KB (51200 bytes)
            // Use title field to exceed the limit since it's a valid field
            StringBuilder sb = new StringBuilder("{\"title\":\"");
            sb.append("x".repeat(52000));
            sb.append("\",\"category\":\"5F\",\"selectedKeywords\":[],\"equipment\":{},");
            sb.append("\"deploymentOrder\":[],\"floorSelections\":[],\"sectionNotes\":{}}");

            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validate(sb.toString(), "5F")
            );

            // Granular error code for size limit exceeded
            assertEquals("SIZE_EXCEEDED", exception.getErrorCode());
        }

        @Test
        @DisplayName("Should pass when content is under 50KB limit")
        void validate_ContentUnderLimit_Passes() {
            setupMocksForValidIds();
            // Use createValidContent which is well under 50KB
            assertDoesNotThrow(() -> validator.validate(createValidContent(), "5F"));
        }
    }

    @Nested
    @DisplayName("Note Size Tests")
    class NoteSizeTests {

        @Test
        @DisplayName("Should throw exception when single note exceeds 1KB")
        void validate_NoteTooLarge_ThrowsException() {
            // Create a note content that exceeds 1KB when serialized
            String largeNoteContent = "x".repeat(1100);
            // Need valid equipment (all 12 sinners) to reach note size validation
            String content = String.format("""
                {
                    "selectedKeywords": [],
                    "equipment": {
                        "01": {"identity": {"id": "10101"}, "egos": {"ZAYIN": {"id": "20101"}}},
                        "02": {"identity": {"id": "10201"}, "egos": {"ZAYIN": {"id": "20201"}}},
                        "03": {"identity": {"id": "10301"}, "egos": {"ZAYIN": {"id": "20301"}}},
                        "04": {"identity": {"id": "10401"}, "egos": {"ZAYIN": {"id": "20401"}}},
                        "05": {"identity": {"id": "10501"}, "egos": {"ZAYIN": {"id": "20501"}}},
                        "06": {"identity": {"id": "10601"}, "egos": {"ZAYIN": {"id": "20601"}}},
                        "07": {"identity": {"id": "10701"}, "egos": {"ZAYIN": {"id": "20701"}}},
                        "08": {"identity": {"id": "10801"}, "egos": {"ZAYIN": {"id": "20801"}}},
                        "09": {"identity": {"id": "10901"}, "egos": {"ZAYIN": {"id": "20901"}}},
                        "10": {"identity": {"id": "11001"}, "egos": {"ZAYIN": {"id": "21001"}}},
                        "11": {"identity": {"id": "11101"}, "egos": {"ZAYIN": {"id": "21101"}}},
                        "12": {"identity": {"id": "11201"}, "egos": {"ZAYIN": {"id": "21201"}}}
                    },
                    "deploymentOrder": [],
                    "floorSelections": [],
                    "sectionNotes": {
                        "floor-1": {"content": {"type": "doc", "text": "%s"}}
                    }
                }
                """, largeNoteContent);

            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validate(content, "5F")
            );

            assertEquals("SIZE_EXCEEDED", exception.getErrorCode());
        }

        @Test
        @DisplayName("Should pass when note is under 1KB limit")
        void validate_NoteUnderLimit_Passes() {
            setupMocksForValidIds();
            String noteContent = "Short note";
            // Use createValidContent and add a note section
            String content = createValidContent().replace(
                    "\"sectionNotes\": {}",
                    "\"sectionNotes\": {\"floor-1\": {\"content\": {\"type\": \"doc\", \"text\": \"" + noteContent + "\"}}}"
            );

            assertDoesNotThrow(() -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should pass with empty sectionNotes")
        void validate_EmptySectionNotes_Passes() {
            setupMocksForValidIds();
            // createValidContent already has empty sectionNotes
            assertDoesNotThrow(() -> validator.validate(createValidContent(), "5F"));
        }
    }

    @Nested
    @DisplayName("Empty/Null Content Tests")
    class EmptyNullContentTests {

        @Test
        @DisplayName("Should throw exception for null content")
        void validate_NullContent_ThrowsException() {
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validate(null, "5F")
            );

            assertEquals("EMPTY_CONTENT", exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw exception for empty content")
        void validate_EmptyContent_ThrowsException() {
            assertThrows(PlannerValidationException.class, () -> validator.validate("", "5F"));
        }

        @Test
        @DisplayName("Should throw exception for blank content")
        void validate_BlankContent_ThrowsException() {
            assertThrows(PlannerValidationException.class, () -> validator.validate("   ", "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-JSON content")
        void validate_NonJsonContent_ThrowsException() {
            assertThrows(PlannerValidationException.class, () -> validator.validate("not json", "5F"));
        }

        @Test
        @DisplayName("Should throw exception for JSON array instead of object")
        void validate_JsonArrayContent_ThrowsException() {
            assertThrows(PlannerValidationException.class, () -> validator.validate("[]", "5F"));
        }

        @Test
        @DisplayName("Should throw exception for JSON primitive instead of object")
        void validate_JsonPrimitiveContent_ThrowsException() {
            assertThrows(PlannerValidationException.class, () -> validator.validate("\"string\"", "5F"));
        }
    }

    @Nested
    @DisplayName("Sinner Index Validation Tests")
    class SinnerIndexTests {

        @Test
        @DisplayName("Should pass with valid sinner indices (01-12) in equipment keys")
        void validate_ValidSinnerIndicesInEquipment_Passes() {
            setupMocksForValidIds();
            assertDoesNotThrow(() -> validator.validate(createValidContent(), "5F"));
        }

        @Test
        @DisplayName("Should throw exception for sinner index 0 in equipment (1-indexed)")
        void validate_SinnerIndex0InEquipment_ThrowsException() {
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

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for sinner index above 12 in equipment")
        void validate_SinnerIndexAbove12InEquipment_ThrowsException() {
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

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-numeric equipment key")
        void validate_NonNumericEquipmentKey_ThrowsException() {
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

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should pass with valid sinner indices (0-11) in deploymentOrder")
        void validate_ValidSinnerIndicesInDeploymentOrder_Passes() {
            setupMocksForValidIds();
            // Use createValidContent and replace deploymentOrder with all valid indices
            String content = createValidContent().replace(
                    "\"deploymentOrder\": [0, 1, 2, 3, 4, 5]",
                    "\"deploymentOrder\": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]"
            );

            assertDoesNotThrow(() -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for negative sinner index in deploymentOrder")
        void validate_NegativeSinnerIndexInDeploymentOrder_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [-1, 0, 1],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for sinner index above 11 in deploymentOrder")
        void validate_SinnerIndexAbove11InDeploymentOrder_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [0, 12],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-numeric value in deploymentOrder")
        void validate_NonNumericDeploymentOrder_ThrowsException() {
            String content = """
                {
                    "selectedKeywords": [],
                    "equipment": {},
                    "deploymentOrder": [0, "abc"],
                    "floorSelections": [],
                    "sectionNotes": {}
                }
                """;

            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Start Buff Validation Tests")
    class StartBuffValidationTests {

        @Test
        @DisplayName("Should pass with valid start buff IDs")
        void validate_ValidStartBuffIds_Passes() {
            setupMocksForValidIds();
            // createFullContent has selectedBuffIds: [100, 201, 302]
            assertDoesNotThrow(() -> validator.validate(createFullContent(), "5F"));
        }

        @Test
        @DisplayName("Should pass with empty selectedBuffIds")
        void validate_EmptyStartBuffIds_Passes() {
            setupMocksForValidIdsWithoutBuffs();
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [],"
            );
            assertDoesNotThrow(() -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should pass with max 10 start buffs")
        void validate_Max10StartBuffs_Passes() {
            setupMocksForValidIds();
            // All 10 base buffs with different enhancement levels
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [100, 201, 302, 103, 204, 305, 106, 207, 308, 109],"
            );
            assertDoesNotThrow(() -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception when start buffs exceed 10")
        void validate_ExceedMax10StartBuffs_ThrowsException() {
            // Fails at max count check, never reaches buff ID or gift validation
            setupMocksForValidIdsWithoutBuffsAndGifts();
            // 11 buffs - exceeds limit
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 200],"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for duplicate base buff IDs (same buff, different enhancement)")
        void validate_DuplicateBaseBuffId_ThrowsException() {
            setupMocksForValidIdsWithoutGifts();
            // 100 and 200 have the same base ID (00)
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [100, 200],"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for duplicate base buff IDs (base and ++)")
        void validate_DuplicateBaseBuffIdBaseAndPlusPlus_ThrowsException() {
            setupMocksForValidIdsWithoutGifts();
            // 101 and 301 have the same base ID (01)
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [101, 301],"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-number in selectedBuffIds")
        void validate_NonNumberInBuffIds_ThrowsException() {
            // Fails at type check before any ID validation
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [100, \"invalid\"],"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for buff ID not found in game data")
        void validate_BuffIdNotInGameData_ThrowsException() {
            // Fails in buff validation, never reaches gift validation
            setupMocksForValidIdsWithoutGifts();
            when(gameDataRegistry.hasStartBuff("999")).thenReturn(false);

            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [100, 999],"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for invalid base ID (outside 00-09)")
        void validate_InvalidBaseBuffId_ThrowsException() {
            // Fails in buff validation, never reaches gift validation
            setupMocksForValidIdsWithoutGifts();
            // Mock returns true but base ID 15 is invalid (valid: 00-09)
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201],",
                    "\"selectedBuffIds\": [115],"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Start Gift Validation Tests")
    class StartGiftValidationTests {

        @Test
        @DisplayName("Should pass with valid keyword and gift IDs from pool")
        void validate_ValidKeywordAndGiftIds_Passes() {
            setupMocksForValidIds();
            // createValidContent has Combustion keyword with 9001 which is in the pool
            assertDoesNotThrow(() -> validator.validate(createValidContent(), "5F"));
        }

        @Test
        @DisplayName("Should pass with null keyword and empty gift IDs")
        void validate_NullKeywordEmptyGifts_Passes() {
            setupMocksForValidIdsWithoutGifts();
            String content = createValidContent()
                    .replace("\"selectedGiftKeyword\": \"Combustion\",", "\"selectedGiftKeyword\": null,")
                    .replace("\"selectedGiftIds\": [\"9001\"],", "\"selectedGiftIds\": [],");
            assertDoesNotThrow(() -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should pass with absent keyword and empty gift IDs")
        void validate_AbsentKeywordEmptyGifts_Passes() {
            setupMocksForValidIdsWithoutGifts();
            // Remove keyword and empty gift IDs
            String content = createValidContent()
                    .replace("\"selectedGiftKeyword\": \"Combustion\",", "")
                    .replace("\"selectedGiftIds\": [\"9001\"],", "\"selectedGiftIds\": [],");
            assertDoesNotThrow(() -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should pass with multiple gift IDs from same keyword pool")
        void validate_MultipleGiftsFromSamePool_Passes() {
            setupMocksForValidIds();
            // Combustion pool has 9001, 9009, 9103
            String content = createValidContent().replace(
                    "\"selectedGiftIds\": [\"9001\"],",
                    "\"selectedGiftIds\": [\"9001\", \"9009\", \"9103\"],"
            );
            assertDoesNotThrow(() -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for gift IDs without keyword")
        void validate_GiftIdsWithoutKeyword_ThrowsException() {
            // Fails at keyword check, never reaches pool validation
            setupMocksForValidIdsWithoutGifts();
            String content = createValidContent()
                    .replace("\"selectedGiftKeyword\": \"Combustion\",", "\"selectedGiftKeyword\": null,");
            // Still has selectedGiftIds: ["9001"]
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for invalid keyword")
        void validate_InvalidKeyword_ThrowsException() {
            setupBaseMocks();
            setupStartBuffMocks();
            when(gameDataRegistry.hasStartGiftKeyword("InvalidKeyword")).thenReturn(false);

            String content = createValidContent().replace(
                    "\"selectedGiftKeyword\": \"Combustion\",",
                    "\"selectedGiftKeyword\": \"InvalidKeyword\","
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for gift ID not in keyword pool")
        void validate_GiftIdNotInPool_ThrowsException() {
            setupBaseMocks();
            setupStartBuffMocks();
            when(gameDataRegistry.hasStartGiftKeyword(anyString())).thenReturn(true);
            // Pool only contains 9001, 9009, 9103 - but content has 9999
            when(gameDataRegistry.getStartGiftPool(anyString())).thenReturn(Set.of("9001", "9009", "9103"));

            String content = createValidContent().replace(
                    "\"selectedGiftIds\": [\"9001\"],",
                    "\"selectedGiftIds\": [\"9001\", \"9999\"],"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for duplicate gift IDs")
        void validate_DuplicateGiftIds_ThrowsException() {
            // Duplicate check happens after first ID is validated
            setupMocksForGiftDuplicateFailure();
            String content = createValidContent().replace(
                    "\"selectedGiftIds\": [\"9001\"],",
                    "\"selectedGiftIds\": [\"9001\", \"9001\"],"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-string gift ID")
        void validate_NonStringGiftId_ThrowsException() {
            // Type check happens before ID lookup
            setupMocksForGiftTypeFailure();
            String content = createValidContent().replace(
                    "\"selectedGiftIds\": [\"9001\"],",
                    "\"selectedGiftIds\": [9001],"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Comprehensive/Observation Gift Validation Tests")
    class ComprehensiveGiftValidationTests {

        @Test
        @DisplayName("Should pass with valid comprehensiveGiftIds")
        void validate_ValidComprehensiveGiftIds_Passes() {
            setupMocksForValidIds();
            String content = createFullContent();
            assertDoesNotThrow(() -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for duplicate comprehensiveGiftIds")
        void validate_DuplicateComprehensiveGiftIds_ThrowsException() {
            // Duplicate check happens after first ID is validated
            setupMocksForGiftDuplicateFailure();
            String content = createFullContent().replace(
                    "\"comprehensiveGiftIds\": [\"19050\"]",
                    "\"comprehensiveGiftIds\": [\"19050\", \"19050\"]"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-string comprehensiveGiftIds element")
        void validate_NonStringComprehensiveGiftId_ThrowsException() {
            // Type check happens before ID lookup
            setupMocksForGiftTypeFailure();
            String content = createFullContent().replace(
                    "\"comprehensiveGiftIds\": [\"19050\"]",
                    "\"comprehensiveGiftIds\": [19050]"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for invalid comprehensiveGiftIds")
        void validate_InvalidComprehensiveGiftId_ThrowsException() {
            // ID lookup fails - need hasEgoGift mock
            setupMocksForGiftDuplicateFailure();
            when(gameDataRegistry.hasEgoGift("99999")).thenReturn(false);

            String content = createFullContent().replace(
                    "\"comprehensiveGiftIds\": [\"19050\"]",
                    "\"comprehensiveGiftIds\": [\"99999\"]"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for duplicate observationGiftIds")
        void validate_DuplicateObservationGiftIds_ThrowsException() {
            // Duplicate check happens after first ID is validated
            setupMocksForGiftDuplicateFailure();
            String content = createFullContent().replace(
                    "\"observationGiftIds\": [\"9100\"]",
                    "\"observationGiftIds\": [\"9100\", \"9100\"]"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-string observationGiftIds element")
        void validate_NonStringObservationGiftId_ThrowsException() {
            // Type check happens before ID lookup
            setupMocksForGiftTypeFailure();
            String content = createFullContent().replace(
                    "\"observationGiftIds\": [\"9100\"]",
                    "\"observationGiftIds\": [9100]"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Floor Selection Gift Validation Tests")
    class FloorSelectionGiftValidationTests {

        @Test
        @DisplayName("Should pass with valid floor giftIds")
        void validate_ValidFloorGiftIds_Passes() {
            setupMocksForValidIds();
            String content = createValidContent();
            assertDoesNotThrow(() -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for duplicate giftIds within same floor")
        void validate_DuplicateFloorGiftIds_ThrowsException() {
            // Floor validation fails before buff/gift pool validation
            setupMocksForValidIdsWithoutBuffsAndGifts();
            String content = createValidContent().replace(
                    "\"giftIds\": [\"9002\"]",
                    "\"giftIds\": [\"9002\", \"9002\"]"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for non-string floor giftIds element")
        void validate_NonStringFloorGiftId_ThrowsException() {
            // Floor validation fails before buff/gift pool validation
            setupMocksForValidIdsWithoutBuffsAndGifts();
            String content = createValidContent().replace(
                    "\"giftIds\": [\"9002\"]",
                    "\"giftIds\": [9002]"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for invalid floor giftIds")
        void validate_InvalidFloorGiftId_ThrowsException() {
            // Floor validation fails before buff/gift pool validation
            setupMocksForValidIdsWithoutBuffsAndGifts();
            when(gameDataRegistry.hasEgoGift("99999")).thenReturn(false);

            String content = createValidContent().replace(
                    "\"giftIds\": [\"9002\"]",
                    "\"giftIds\": [\"99999\"]"
            );
            assertThrows(PlannerValidationException.class, () -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should allow same giftId across different floors")
        void validate_SameGiftIdAcrossFloors_Passes() {
            setupMocksForValidIds();
            String content = createValidContent().replace(
                    "\"floorSelections\": [{\"themePackId\": \"1001\", \"difficulty\": 0, \"giftIds\": [\"9002\"]}]",
                    "\"floorSelections\": [{\"themePackId\": \"1001\", \"difficulty\": 0, \"giftIds\": [\"9002\"]}, {\"themePackId\": \"1002\", \"difficulty\": 0, \"giftIds\": [\"9002\"]}]"
            );
            assertDoesNotThrow(() -> validator.validate(content, "5F"));
        }
    }

    @Nested
    @DisplayName("Granular Error Codes")
    class GranularErrorCodeTests {

        @Test
        @DisplayName("Gift not affordable for theme pack should produce GIFT_NOT_AFFORDABLE sub-error")
        void validate_GiftNotAffordable_ProducesGiftNotAffordableCode() {
            setupMocksForUnaffordableGifts();

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validator.validate(createValidContent(), "5F"));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "GIFT_NOT_AFFORDABLE".equals(e.code())),
                    "Expected GIFT_NOT_AFFORDABLE in sub-errors");
        }

        @Test
        @DisplayName("Duplicate gift ID in floor giftIds should produce DUPLICATE_VALUE sub-error")
        void validate_DuplicateFloorGiftId_ProducesDuplicateValueCode() {
            setupMocksForValidIds();
            String content = createValidContent().replace(
                    "\"giftIds\": [\"9002\"]",
                    "\"giftIds\": [\"9002\", \"9002\"]"
            );

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validator.validate(content, "5F"));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "DUPLICATE_VALUE".equals(e.code())),
                    "Expected DUPLICATE_VALUE in sub-errors");
        }

        @Test
        @DisplayName("Duplicate base ID in selectedBuffIds should produce DUPLICATE_VALUE sub-error")
        void validate_DuplicateBuffBaseId_ProducesDuplicateValueCode() {
            setupMocksForValidIds();
            // 100 and 200 share base ID 0 (100 % 100 == 0, 200 % 100 == 0)
            String content = createValidContent().replace(
                    "\"selectedBuffIds\": [100, 201]",
                    "\"selectedBuffIds\": [100, 200]"
            );

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validator.validate(content, "5F"));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "DUPLICATE_VALUE".equals(e.code())),
                    "Expected DUPLICATE_VALUE in sub-errors");
        }

        @Test
        @DisplayName("Duplicate gift ID in selectedGiftIds should produce DUPLICATE_VALUE sub-error")
        void validate_DuplicateSelectedGiftId_ProducesDuplicateValueCode() {
            setupMocksForValidIds();
            String content = createValidContent().replace(
                    "\"selectedGiftIds\": [\"9001\"]",
                    "\"selectedGiftIds\": [\"9001\", \"9001\"]"
            );

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validator.validate(content, "5F"));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "DUPLICATE_VALUE".equals(e.code())),
                    "Expected DUPLICATE_VALUE in sub-errors");
        }

        @Test
        @DisplayName("Floor N having themePackId when floor N-1 lacks it should produce INVALID_SEQUENCE sub-error")
        void validate_FloorPrerequisiteViolation_ProducesInvalidSequenceCode() {
            setupMocksForValidIds();
            // Floor 0 has null themePackId; floor 1 has one → prerequisite violation
            String content = createValidContent().replace(
                    "{\"themePackId\": \"1001\", \"difficulty\": 0, \"giftIds\": [\"9002\"]}",
                    "{\"themePackId\": null, \"difficulty\": 0, \"giftIds\": []}, {\"themePackId\": \"1001\", \"difficulty\": 0, \"giftIds\": []}"
            );

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validator.validate(content, "5F"));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "INVALID_SEQUENCE".equals(e.code())),
                    "Expected INVALID_SEQUENCE in sub-errors");
        }

        @Test
        @DisplayName("selectedGiftIds present without selectedGiftKeyword should produce INVALID_SEQUENCE sub-error")
        void validate_GiftsWithoutKeyword_ProducesInvalidSequenceCode() {
            // No start gift keyword mocks needed: validateStartGiftIds returns early on null keyword
            setupMocksForValidIdsWithoutGifts();
            String content = createValidContent().replace(
                    "\"selectedGiftKeyword\": \"Combustion\"",
                    "\"selectedGiftKeyword\": null"
            );

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validator.validate(content, "5F"));

            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "INVALID_SEQUENCE".equals(e.code())),
                    "Expected INVALID_SEQUENCE in sub-errors");
        }
    }

    @Nested
    @DisplayName("Multi-Error Collection")
    class MultiErrorCollectionTests {

        @Test
        @DisplayName("Two independent errors should both appear in sub-errors of combined exception")
        void validate_TwoIndependentErrors_CollectsBothInSubErrors() {
            setupMocksForUnaffordableGifts();
            // deploymentOrder[0] = 99 is OOB (0–11) → VALUE_OUT_OF_RANGE
            // floor giftIds[0] is not affordable → GIFT_NOT_AFFORDABLE
            String content = createValidContent().replace(
                    "\"deploymentOrder\": [0, 1, 2, 3, 4, 5]",
                    "\"deploymentOrder\": [99]"
            );

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validator.validate(content, "5F"));

            assertEquals("VALIDATION_ERROR", ex.getErrorCode());
            assertTrue(ex.getSubErrors().size() >= 2, "Expected at least 2 sub-errors");
            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "VALUE_OUT_OF_RANGE".equals(e.code())),
                    "Expected VALUE_OUT_OF_RANGE in sub-errors");
            assertTrue(ex.getSubErrors().stream().anyMatch(e -> "GIFT_NOT_AFFORDABLE".equals(e.code())),
                    "Expected GIFT_NOT_AFFORDABLE in sub-errors");
        }

        @Test
        @DisplayName("Single validation error should produce combined exception with exactly one sub-error")
        void validate_SingleError_ProducesCombinedExceptionWithOneSubError() {
            setupMocksForUnaffordableGifts();

            PlannerValidationException ex = assertThrows(PlannerValidationException.class,
                    () -> validator.validate(createValidContent(), "5F"));

            assertEquals("VALIDATION_ERROR", ex.getErrorCode());
            assertEquals(1, ex.getSubErrors().size());
            assertEquals("GIFT_NOT_AFFORDABLE", ex.getSubErrors().get(0).code());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should pass with boundary sinner indices (01 and 12 for equipment, 0 and 11 for deployment)")
        void validate_BoundarySinnerIndices_Passes() {
            setupMocksForValidIds();
            // createValidContent already uses boundary values:
            // - Equipment keys: "01" through "12" (all 12 sinners)
            // - DeploymentOrder: includes 0 and can include 11
            String content = createValidContent().replace(
                    "\"deploymentOrder\": [0, 1, 2, 3, 4, 5]",
                    "\"deploymentOrder\": [0, 11]"  // Boundary values 0 and 11
            );

            assertDoesNotThrow(() -> validator.validate(content, "5F"));
        }

        @Test
        @DisplayName("Should throw exception for malformed JSON")
        void validate_MalformedJson_ThrowsException() {
            assertThrows(PlannerValidationException.class,
                    () -> validator.validate("{\"title\": \"unclosed string}", "5F"));
        }

        @Test
        @DisplayName("Should handle deeply nested but valid structure")
        void validate_ValidDeepNesting_Passes() {
            setupMocksForValidIds();
            // Use createFullContent which has all optional fields and complete equipment
            assertDoesNotThrow(() -> validator.validate(createFullContent(), "5F"));
        }

        @Test
        @DisplayName("Should handle unicode characters in title")
        void validate_UnicodeTitle_Passes() {
            setupMocksForValidIds();
            // Use createValidContent with unicode title
            String content = createValidContent().replace(
                    "\"title\": \"Test Planner\"",
                    "\"title\": \"유니코드 테스트 タイトル\""
            );

            assertDoesNotThrow(() -> validator.validate(content, "5F"));
        }
    }
}
