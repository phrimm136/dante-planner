package org.danteplanner.backend.planner.validation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSchemaPatternSubsetTest {

    private record Place(String pattern, int at, int end, int depth, Optional<Kind> previous) {
    }

    private record Kind(String name, Pattern shape, int depthChange, Predicate<Place> allowed) {
    }

    private record Token(Kind kind, String text, int end) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path SCHEMA = Path.of("../testdata/planner-content.schema.json");

    private static final String LITERAL = "[^\\\\^$.|?*+()\\[\\]{}\\uD800-\\uDFFF]";
    private static final String CLASS_LITERAL = "[^\\\\^$.|?*+()\\[\\]{}\\-&\\uD800-\\uDFFF]";
    private static final String ASCII_CLASS_LITERAL = "[!-~&&" + CLASS_LITERAL + "]";

    private static final Predicate<Place> ANYWHERE = place -> true;
    private static final Predicate<Place> INSIDE_A_GROUP = place -> place.depth() > 0;

    private static final Kind LITERAL_KIND = kind("literal", LITERAL, 0, ANYWHERE);
    private static final Kind ESCAPE = kind("escape", "\\\\(?:d|[\\\\^$.|?*+()\\[\\]{}\\-])", 0, ANYWHERE);
    private static final Kind CLASS = kind("class", "\\[\\^?(?:" + ASCII_CLASS_LITERAL + "-" + ASCII_CLASS_LITERAL
            + "|" + CLASS_LITERAL + "|\\\\d)+\\]", 0, ANYWHERE);
    private static final Kind GROUP_END = kind("group end", "\\)", -1, INSIDE_A_GROUP);
    private static final Set<Kind> ATOMS = Set.of(LITERAL_KIND, ESCAPE, CLASS, GROUP_END);

    private static final List<Kind> ALLOWED = List.of(
            kind("start anchor", "\\^", 0, place -> place.at() == 0),
            kind("end anchor", "\\$", 0, place -> place.end() == place.pattern().length()),
            LITERAL_KIND,
            ESCAPE,
            kind("non-capturing group", "\\(\\?:", 1, ANYWHERE),
            kind("group", "\\((?!\\?)", 1, ANYWHERE),
            GROUP_END,
            kind("alternation", "\\|", 0, INSIDE_A_GROUP),
            CLASS,
            kind("quantifier", "(?:[?*+]|\\{\\d+(?:,\\d*)?\\})\\??", 0,
                    place -> place.previous().filter(ATOMS::contains).isPresent()));

    @Test
    void schemaPatterns_WhenScannedAgainstTheSharedSubset_FindNothingOutsideIt() throws IOException {
        JsonNode schema = MAPPER.readTree(SCHEMA.toFile());

        assertThat(patterns(schema)).isNotEmpty();
        assertThat(findings(schema)).isEmpty();
    }

    @Test
    void schemaPatterns_WhenACopyOfTheSchemaCarriesAWhitespaceClass_NameThePatternAndTheToken()
            throws IOException {
        JsonNode schema = MAPPER.readTree(SCHEMA.toFile());
        ((ObjectNode) schema.path("properties").path("floorSelections").path("items").path("properties")
                .path("giftIds").path("items")).put("pattern", "^\\s*[12]?9\\d{3}$");

        assertThat(findings(schema)).containsExactly("^\\s*[12]?9\\d{3}$: `\\s` at 1");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"^a", "a$", "a", "\\d", "\\.", "\\-", "(?:a)", "(a)", "(a|b)", "[a-z]", "[^a-z\\d_]",
            "a?", "a*", "a+", "a{2}", "a{2,}", "a{2,3}", "a+?", "a{2,3}?", "(a)?", "[ab]*", "\\d{4}"})
    void findings_WhenAPatternUsesOnlyAllowedTokens_AreEmpty(String pattern) {
        assertThat(findings(pattern)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiterString = "=>", textBlock = """
            ^\\s$      => `\\s` at 1
            ^a.c$      => `.` at 2
            ^[]$       => `[` at 1
            ^\\0$      => `\\0` at 1
            ^(a)\\1$   => `\\1` at 4
            ^(?<=a)b$  => `(` at 1
            ^{a}$      => `{` at 1
            ^a{$       => `{` at 2
            a^b        => `^` at 1
            ^[a&&b]$   => `[` at 1
            """)
    void findings_WhenAPatternLeavesTheSharedSubset_NameTheTokenAndItsOffset(String pattern, String finding) {
        assertThat(findings(pattern)).containsExactly(pattern + ": " + finding);
    }

    private static List<String> findings(JsonNode schema) {
        return patterns(schema).stream().flatMap(pattern -> findings(pattern).stream()).toList();
    }

    private static List<String> findings(String pattern) {
        int at = 0;
        int depth = 0;
        Optional<Kind> previous = Optional.empty();
        while (at < pattern.length()) {
            Optional<Token> token = token(pattern, at);
            Place place = new Place(pattern, at, token.map(Token::end).orElse(at), depth, previous);
            if (token.filter(found -> found.kind().allowed().test(place)).isEmpty()) {
                String text = token.map(Token::text).orElse(pattern.substring(at, unknownEnd(pattern, at)));
                return List.of(pattern + ": `" + text + "` at " + at);
            }
            depth += token.get().kind().depthChange();
            previous = token.map(Token::kind);
            at = token.get().end();
        }
        return List.of();
    }

    private static Optional<Token> token(String pattern, int at) {
        return ALLOWED.stream()
                .map(kind -> Map.entry(kind, kind.shape().matcher(pattern).useTransparentBounds(true)
                        .region(at, pattern.length())))
                .filter(entry -> entry.getValue().lookingAt())
                .findFirst()
                .map(entry -> new Token(entry.getKey(), entry.getValue().group(), entry.getValue().end()));
    }

    private static int unknownEnd(String pattern, int at) {
        return Math.min(pattern.charAt(at) == '\\' ? at + 2 : at + 1, pattern.length());
    }

    private static List<String> patterns(JsonNode node) {
        Stream<String> own = Stream.concat(
                Stream.of(node.path("pattern")).filter(JsonNode::isTextual).map(JsonNode::asText),
                node.path("patternProperties").properties().stream().map(Map.Entry::getKey));
        Stream<String> nested = StreamSupport.stream(node.spliterator(), false)
                .flatMap(child -> patterns(child).stream());
        return Stream.concat(own, nested).toList();
    }

    private static Kind kind(String name, String shape, int depthChange, Predicate<Place> allowed) {
        return new Kind(name, Pattern.compile(shape), depthChange, allowed);
    }
}
