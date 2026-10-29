package org.danteplanner.backend.planner.validation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ContentSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path TESTDATA_SCHEMA = Path.of("../testdata/planner-content.schema.json");
    private static final String CLASSPATH_SCHEMA = "/planner/planner-content.schema.json";

    private final ContentSchema schema = ContentSchema.planner();

    @Test
    void schemaFile_WhenLoadedFromTheClasspath_EqualsTheTestdataFile() throws IOException {
        try (InputStream loaded = ContentSchemaTest.class.getResourceAsStream(CLASSPATH_SCHEMA)) {
            assertThat(loaded).as("%s on the classpath", CLASSPATH_SCHEMA).isNotNull();
            assertThat(loaded.readAllBytes()).isEqualTo(Files.readAllBytes(TESTDATA_SCHEMA));
        }
    }

    static Stream<Arguments> storedShapeFamilies() {
        return Stream.of(
                Arguments.of("twelve sinners, ZAYIN only, five floors", edit(content -> {
                })),
                Arguments.of("an optional E.G.O slot beside ZAYIN", edit(content -> egos(content, "4")
                        .set("TETH", ego("20402", 3)))),
                Arguments.of("every E.G.O slot", edit(content -> egos(content, "7")
                        .set("TETH", ego("20702", 1))
                        .set("HE", ego("20703", 2))
                        .set("WAW", ego("20704", 3))
                        .set("ALEPH", ego("20705", 4)))),
                Arguments.of("a floor without a theme pack", edit(content -> ((ObjectNode) content
                        .path("floorSelections").path(4)).putNull("themePackId"))),
                Arguments.of("no start gift keyword", edit(content -> {
                    content.putNull("selectedGiftKeyword");
                    content.putArray("selectedGiftIds");
                })),
                Arguments.of("section notes with editor content", edit(content -> ((ObjectNode) content
                        .path("sectionNotes")).set("floor-1", MAPPER.createObjectNode()
                        .set("content", MAPPER.createObjectNode().put("type", "doc").putArray("content"))))),
                Arguments.of("floors past the category's count", edit(content -> content.withArray("floorSelections")
                        .add(MAPPER.createObjectNode().put("themePackId", "1006").put("difficulty", 1)
                                .set("giftIds", MAPPER.createArrayNode().add("19002"))))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("storedShapeFamilies")
    void violations_WhenAStoredShapeFamilyIsChecked_ReportsNothing(String family, Consumer<ObjectNode> shape) {
        ObjectNode content = validContent();
        shape.accept(content);

        assertThat(schema.violations(content)).isEmpty();
    }

    @Test
    void violations_WhenAStringOrNullFieldHoldsANumber_ReportsOneInvalidFieldType() {
        ObjectNode content = validContent().put("selectedGiftKeyword", 5);

        assertThat(schema.violations(content)).extracting(Violation::code, violation -> violation.path().render())
                .containsExactly(tuple(ErrorCode.INVALID_FIELD_TYPE, "selectedGiftKeyword"));
    }

    @Test
    void violations_WhenAFieldIsNullRatherThanAbsent_ReportsItsTypeNotItsAbsence() {
        ObjectNode nullIdentity = validContent();
        ((ObjectNode) nullIdentity.path("equipment").path("3")).putNull("identity");
        ObjectNode absentIdentity = validContent();
        ((ObjectNode) absentIdentity.path("equipment").path("3")).remove("identity");

        assertThat(schema.violations(nullIdentity)).extracting(Violation::code, violation -> violation.path().render())
                .containsExactly(tuple(ErrorCode.INVALID_FIELD_TYPE, "equipment[3].identity"));
        assertThat(schema.violations(absentIdentity)).extracting(Violation::code, violation -> violation.path().render())
                .containsExactly(tuple(ErrorCode.MISSING_REQUIRED_FIELD, "equipment[3].identity"));
    }

    @Test
    void violations_WhenKeysAreUnknownAtDepth_RendersEachAsWrittenByItsParentsKind() {
        ObjectNode content = validContent();
        ((ObjectNode) content.path("equipment").path("1").path("egos")).set("KETHER", ego("20101", 1));
        ((ObjectNode) content.path("skillEAState").path("1")).put("3", 0);
        ((ObjectNode) content.path("equipment")).set("01", content.path("equipment").path("1").deepCopy());

        assertThat(schema.violations(content)).extracting(Violation::code, violation -> violation.path().render()).containsExactly(
                tuple(ErrorCode.UNKNOWN_FIELD, "equipment[01]"),
                tuple(ErrorCode.UNKNOWN_FIELD, "equipment[1].egos.KETHER"),
                tuple(ErrorCode.UNKNOWN_FIELD, "skillEAState[1][3]"));
    }

    @Test
    void violations_WhenAnIntegerLeavesTheSafeRange_ReportsItsType() {
        ObjectNode content = validContent();
        content.withArray("deploymentOrder").add(MAPPER.getNodeFactory().numberNode(9007199254740992L));

        assertThat(schema.violations(content)).extracting(Violation::code, violation -> violation.path().render())
                .containsExactly(tuple(ErrorCode.INVALID_FIELD_TYPE, "deploymentOrder[6]"));
    }

    @Test
    void violations_WhenAStringValueIsExactlyTheLogLimit_QuotesItWhole() {
        String hundred = "x".repeat(100);
        ObjectNode content = validContent();
        ((ObjectNode) content.path("floorSelections").path(0)).put("difficulty", hundred);

        assertThat(schema.violations(content)).extracting(Violation::message).containsExactly(
                "Field 'floorSelections[0].difficulty' has an invalid value: string \"" + hundred + "\"");
    }

    @Test
    void rewrite_WhenAnchoredAtBothEnds_UsesTheStartAndEndOfInput() {
        assertThat(EcmaAnchoredRegexFactory.rewrite("^\\d{4}$")).isEqualTo("^\\d{4}\\z");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"a\\$", "a[$]"})
    void rewrite_WhenTheFinalDollarIsEscapedOrInsideAClass_LeavesItLiteral(String regex) {
        assertThat(EcmaAnchoredRegexFactory.rewrite(regex)).isEqualTo(regex);
        assertThat(new EcmaAnchoredRegexFactory().getRegularExpression(regex).matches("xa$")).isTrue();
    }

    @Test
    void rewrite_WhenTheFinalDollarFollowsAnEscapedBackslash_AnchorsIt() {
        assertThat(EcmaAnchoredRegexFactory.rewrite("a\\\\$")).isEqualTo("a\\\\\\z");
    }

    @Test
    void rewrite_WhenThePatternHasNoAnchors_ReturnsItUnchangedAndSearches() {
        assertThat(EcmaAnchoredRegexFactory.rewrite("\\d{4}")).isEqualTo("\\d{4}");
        assertThat(new EcmaAnchoredRegexFactory().getRegularExpression("\\d{4}").matches("x1002y")).isTrue();
    }

    @Test
    void anchoredPattern_WhenTheValueIsExactlyTheDigits_MatchesIt() {
        assertThat(new EcmaAnchoredRegexFactory().getRegularExpression("^\\d{4}$").matches("1002")).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"1002\n", "1002\r\n", "1002 ", "\uFF11\uFF10\uFF10\uFF12"})
    void anchoredPattern_WhenTheDigitsCarryALineBreakASpaceOrAreFullwidth_RejectsIt(String value) {
        assertThat(new EcmaAnchoredRegexFactory().getRegularExpression("^\\d{4}$").matches(value)).isFalse();
    }

    private static Consumer<ObjectNode> edit(Consumer<ObjectNode> change) {
        return change;
    }

    private static ObjectNode egos(ObjectNode content, String sinner) {
        return (ObjectNode) content.path("equipment").path(sinner).path("egos");
    }

    private static ObjectNode ego(String id, int threadspin) {
        return MAPPER.createObjectNode().put("id", id).put("threadspin", threadspin);
    }

    private static ObjectNode validContent() {
        return (ObjectNode) json(TestDataFactory.VALID_CONTENT);
    }

    private static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }
}
