package org.danteplanner.backend.planner.validation;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ListAssert;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.danteplanner.backend.planner.validation.floor.Stage;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

class IdentityLevelCapTest {

    private static final int MD6 = 6;
    private static final int MD7 = 7;
    private static final String STORED_LEVEL = "\"level\":45";
    private static final String LEVEL_PATH = "equipment[1].identity.level";

    private final PlannerContentValidator validator = TestDataFactory.contentValidator();

    @Test
    void parse_WhenAnMd6LevelIsAboveItsCap_StoresTheCapAsAMigration() {
        PlannerDocument document = parse(withLevel("60"), MD6);

        assertThat(document.json()).contains("\"level\":55").doesNotContain("\"level\":60");
        assertThat(document.migrated()).isTrue();
        assertThat(document.content().identities().get("1").level()).isEqualTo(55L);
    }

    @Test
    void parse_WhenAnMd7LevelSitsAtItsCap_StoresTheContentAsReceivedEveryTime() {
        String content = withLevel("60");

        PlannerDocument first = parse(content, MD7);
        PlannerDocument second = parse(first.json(), MD7);

        assertThat(first.json()).isEqualTo(content);
        assertThat(first.migrated()).isFalse();
        assertThat(second.json()).isEqualTo(content);
        assertThat(second.migrated()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"61", "3000000000"})
    void parse_WhenAnMd7LevelIsAboveItsCap_ClipsItToSixtyWithoutAViolation(String level) {
        PlannerDocument document = parse(withLevel(level), MD7);

        assertThat(document.json()).contains("\"level\":60");
        assertThat(document.migrated()).isTrue();
        assertThat(parse(document.json(), MD7).migrated()).isFalse();
    }

    @Test
    void parse_WhenAnMd7LevelIsBelowOne_RejectsItAgainstTheSeasonRange() {
        assertSubErrors(withLevel("0"), MD7).containsExactly(new ValidationError(
                "OUT_OF_RANGE_IDENTITY_LEVEL", LEVEL_PATH, "level value 0 is out of range [1-60]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"60\"", "1.5", "\"99\""})
    void parse_WhenTheLevelIsNotAnInteger_LeavesItToTheTypeCheck(String level) {
        assertSubErrors(withLevel(level), MD7)
                .extracting(ValidationError::code, ValidationError::path)
                .containsExactly(tuple("INVALID_FIELD_TYPE", LEVEL_PATH));
    }

    @Test
    void reparseForCategory_WhenAnMd6PlannerIsStoredAboveItsCap_LeavesTheStoredLevel() {
        String stored = withLevel("60");

        PlannerDocument document = validator.reparseForCategory(stored, MDCategory.F5, Stage.DRAFT);

        assertThat(document.json()).isEqualTo(stored);
        assertThat(document.migrated()).isFalse();
    }

    private PlannerDocument parse(String content, int version) {
        return validator.parse(content, MDCategory.F5, validator.season(version), Stage.DRAFT);
    }

    private ListAssert<ValidationError> assertSubErrors(String content, int version) {
        return assertThatThrownBy(() -> parse(content, version))
                .asInstanceOf(InstanceOfAssertFactories.type(PlannerValidationException.class))
                .extracting(PlannerValidationException::getSubErrors, InstanceOfAssertFactories.list(ValidationError.class));
    }

    private static String withLevel(String level) {
        assertThat(TestDataFactory.VALID_CONTENT).contains(STORED_LEVEL);
        return TestDataFactory.VALID_CONTENT.replaceFirst(STORED_LEVEL, "\"level\":" + level);
    }
}
