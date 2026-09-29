package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ContentPathTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<Path> RECORDED_PATH_FILES = List.of(
            Path.of("../testdata/planner-floor-rules.corpus.json"),
            Path.of("../testdata/floor-exchange-frontend.json"),
            Path.of("../testdata/floor-exchange-backend.json"));
    private static final String GOLDEN_CORPUS = "/validation/golden-corpus.txt";
    private static final Pattern GOLDEN_PATH = Pattern.compile("[A-Za-z]+(?:\\[[^\\] ]+]|\\.[A-Za-z0-9]+)+");
    private static final Pattern SEGMENT = Pattern.compile("^([A-Za-z0-9_]+)|\\.([A-Za-z0-9_]+)|\\[([^\\]]+)]");
    private static final Pattern CANONICAL_INDEX = Pattern.compile("0|[1-9][0-9]*");

    @Test
    void render_WhenPathHasNoSegments_IsEmpty() {
        assertThat(ContentPath.root().render()).isEmpty();
    }

    @Test
    void render_WhenSegmentsMixFieldsIndicesAndKeys_WritesTheDottedBracketGrammar() {
        assertThat(ContentPath.of("floorSelections").render()).isEqualTo("floorSelections");
        assertThat(ContentPath.of("floorSelections").index(3).field("giftIds").index(10).render())
                .isEqualTo("floorSelections[3].giftIds[10]");
        assertThat(ContentPath.of("equipment").key("01").field("identity").render())
                .isEqualTo("equipment[01].identity");
        assertThat(ContentPath.of("skillEAState").key("05").index(0).render()).isEqualTo("skillEAState[05][0]");
        assertThat(ContentPath.of("equipment").field("05").field("egos").render()).isEqualTo("equipment.05.egos");
    }

    @Test
    void render_WhenEveryRecordedPathIsBuiltSegmentBySegment_ReproducesItsText() throws IOException {
        Set<String> recorded = recordedPaths();

        assertThat(recorded).contains("floorSelections", "floorSelections[14]", "floorSelections[0].giftIds[0]",
                "equipment[05].identity", "equipment.05.egos.ZAYIN.id", "skillEAState[05][0]", "category");
        assertThat(recorded).allSatisfy(text -> assertThat(built(text).render()).isEqualTo(text));
    }

    @Test
    void compareTo_WhenIndicesDifferInDigitCount_OrdersThemNumerically() {
        ContentPath gifts = ContentPath.of("floorSelections").index(0).field("giftIds");

        assertThat(gifts.index(2)).isLessThan(gifts.index(10));
        assertThat(ContentPath.of("floorSelections").index(9)).isLessThan(ContentPath.of("floorSelections").index(10));
    }

    @Test
    void compareTo_WhenOnePathExtendsTheOther_OrdersTheShorterFirst() {
        ContentPath floor = ContentPath.of("floorSelections").index(1);

        assertThat(ContentPath.of("floorSelections")).isLessThan(floor);
        assertThat(floor).isLessThan(floor.field("difficulty"));
        assertThat(floor.field("giftIds")).isLessThan(floor.field("giftIds").index(0));
        assertThat(floor.compareTo(ContentPath.of("floorSelections").index(1))).isZero();
    }

    @Test
    void compareTo_WhenFieldsDiffer_OrdersThemByName() {
        ContentPath floor = ContentPath.of("floorSelections").index(0);

        assertThat(floor.field("difficulty")).isLessThan(floor.field("giftIds"));
        assertThat(floor.field("giftIds").index(12)).isLessThan(floor.field("themePackId"));
    }

    @Test
    void compareTo_WhenSegmentKindsDiffer_OrdersFieldThenIndexThenKey() {
        ContentPath equipment = ContentPath.of("equipment");

        assertThat(equipment.field("z")).isLessThan(equipment.index(0));
        assertThat(equipment.index(99)).isLessThan(equipment.key("01"));
        assertThat(equipment.key("01")).isLessThan(equipment.key("02"));
    }

    @Test
    void byPathThenCode_WhenGiftIndicesStraddleTen_OrdersTwoBeforeTen() {
        ContentPath gifts = ContentPath.of("floorSelections").index(0).field("giftIds");
        Violation atTen = new Violation(ErrorCode.INVALID_FIELD_TYPE, gifts.index(10), "ten");
        Violation atTwo = new Violation(ErrorCode.INVALID_FIELD_TYPE, gifts.index(2), "two");

        assertThat(Stream.of(atTen, atTwo).sorted(Violation.BY_PATH_THEN_CODE).toList())
                .containsExactly(atTwo, atTen);
    }

    @Test
    void byPathThenCode_WhenPathsAreEqual_OrdersByCode() {
        ContentPath floor = ContentPath.of("floorSelections").index(1);
        Violation sequence = new Violation(ErrorCode.OUT_OF_ORDER_THEMEPACK, floor, "sequence");
        Violation missing = new Violation(ErrorCode.MISSING_THEMEPACK, floor, "missing");

        assertThat(Stream.of(sequence, missing).sorted(Violation.BY_PATH_THEN_CODE).toList())
                .containsExactly(missing, sequence);
    }

    private static Set<String> recordedPaths() throws IOException {
        Set<String> paths = new TreeSet<>();
        for (Path file : RECORDED_PATH_FILES) {
            MAPPER.readTree(file.toFile()).findValues("path").stream()
                    .map(JsonNode::asText)
                    .forEach(paths::add);
        }
        try (InputStream golden = ContentPathTest.class.getResourceAsStream(GOLDEN_CORPUS)) {
            assertThat(golden).as(GOLDEN_CORPUS).isNotNull();
            Matcher found = GOLDEN_PATH.matcher(new String(golden.readAllBytes(), StandardCharsets.UTF_8));
            while (found.find()) {
                paths.add(found.group());
            }
        }
        return paths;
    }

    private static ContentPath built(String text) {
        ContentPath path = ContentPath.root();
        Matcher segment = SEGMENT.matcher(text);
        int end = 0;
        while (end < text.length() && segment.find(end) && segment.start() == end) {
            if (segment.group(1) != null) {
                path = path.field(segment.group(1));
            } else if (segment.group(2) != null) {
                path = path.field(segment.group(2));
            } else if (CANONICAL_INDEX.matcher(segment.group(3)).matches()) {
                path = path.index(Integer.parseInt(segment.group(3)));
            } else {
                path = path.key(segment.group(3));
            }
            end = segment.end();
        }
        assertThat(end).as("'%s' tokenizes completely", text).isEqualTo(text.length());
        return path;
    }
}
