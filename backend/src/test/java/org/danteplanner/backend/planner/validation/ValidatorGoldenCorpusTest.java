package org.danteplanner.backend.planner.validation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Freezes the error output of every planner-content validator over a corpus of bad inputs.
 *
 * <p>The validators' messages are an API contract the frontend reads, and the traversal helper
 * that is to own their type and uniqueness branching must not alter one byte of them. This class
 * is the gate: it replays each corpus entry through the real validation chain and compares the
 * complete, ordered output — top-level code, combined message, and every sub-error in the order
 * it accumulated — against a checked-in snapshot.
 *
 * <p>Game data comes from the real {@link GameDataRegistry} over a small fixed dataset, so an
 * entry freezes the registry's own id and affordability rules rather than a copy of them, and the
 * snapshot moves only when a validator does.
 *
 * <p>To regenerate: run with {@code GOLDEN_CORPUS_REWRITE=true}, which writes
 * {@code golden-corpus.actual.txt} beside the snapshot; compare the two, justify every changed
 * line, and promote it by copying it over {@code golden-corpus.txt} by hand. Nothing writes the
 * canonical file automatically — a gate that repairs itself gates nothing.
 */
class ValidatorGoldenCorpusTest {

    private static final String SNAPSHOT_RESOURCE = "/validation/golden-corpus.txt";
    private static final Path REWRITE_TARGET =
            Path.of("src/test/resources/validation/golden-corpus.actual.txt");

    private static final String SECTION_MARKER = "### ";
    private static final String ACCEPTED = "accepted";

    private ObjectMapper objectMapper;
    private PlannerContentValidator contentValidator;
    private ContentVersionValidator versionValidator;
    private IdReferenceValidator idReferenceValidator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        GameDataRegistry registry = fixedRegistry(objectMapper);
        idReferenceValidator = new IdReferenceValidator(registry, new SinnerIdValidator());
        contentValidator = new PlannerContentValidator(
                new StructuralValidator(objectMapper,
                        ValidatorGoldenCorpus.MAX_CONTENT_SIZE_BYTES,
                        ValidatorGoldenCorpus.MAX_NOTE_SIZE_BYTES),
                new CategoryValidator(),
                new EquipmentValidator(),
                new SkillStateValidator(),
                idReferenceValidator,
                new StartBuffValidator(registry));
        versionValidator = new ContentVersionValidator(
                ValidatorGoldenCorpus.MD_CURRENT_VERSION, ValidatorGoldenCorpus.RR_AVAILABLE_VERSIONS);
    }

    /**
     * The scenario the traversal conversion is measured against: one array carrying a wrong-typed
     * element and a repeat produces one error for each, in document order, and nothing else.
     */
    @Test
    @DisplayName("mixed-type gift array accumulates both errors")
    void validateGiftIds_WhenArrayMixesTypesAndRepeats_ProducesTypeThenDuplicateError() throws IOException {
        JsonNode root = objectMapper.readTree("{\"selectedGiftIds\":[\"gift_a\",42,\"gift_a\"]}");
        ValidationContext context = new ValidationContext(ValidationPolicy.DRAFT);

        idReferenceValidator.validateGiftIds(root, context);

        assertThat(context.getErrors())
                .extracting(PlannerValidationException::getErrorCode, PlannerValidationException::getMessage)
                .containsExactly(
                        tuple("INVALID_FIELD_TYPE",
                                "Field 'selectedGiftIds[1]' must be string, got number 42"),
                        tuple("DUPLICATE_VALUE", "Duplicate value 'gift_a' in selectedGiftIds"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contentEntryNames")
    void contentChain_WhenCorpusEntryReplayed_MatchesSnapshot(String name) {
        ValidatorGoldenCorpus.ContentEntry entry = CONTENT_ENTRIES.get(name);

        assertThat(outcomeOf(() -> contentValidator.validate(
                entry.content(), entry.category(), entry.policy())))
                .as(OUTPUT_CHANGED, name)
                .isEqualTo(snapshot().get(name));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("versionEntryNames")
    void contentVersion_WhenCorpusEntryReplayed_MatchesSnapshot(String name) {
        ValidatorGoldenCorpus.VersionEntry entry = VERSION_ENTRIES.get(name);

        assertThat(outcomeOf(() -> versionValidator.validateVersionForCreate(
                entry.plannerType(), entry.contentVersion())))
                .as(OUTPUT_CHANGED, name)
                .isEqualTo(snapshot().get(name));
    }

    @Test
    @DisplayName("the snapshot holds exactly the corpus, no more and no less")
    void snapshot_WhenComparedToTheCorpus_CoversEveryEntryExactlyOnce() {
        assertThat(snapshot().keySet())
                .as("a section with no entry is a frozen message nothing produces any more, and an "
                        + "entry with no section is an unfrozen one")
                .containsExactlyElementsOf(corpusNames());
    }

    /**
     * Writes the current output beside the snapshot as {@code golden-corpus.actual.txt}, never
     * over the snapshot itself: promotion stays a deliberate manual copy, so no run can quietly
     * bless a changed message.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "GOLDEN_CORPUS_REWRITE", matches = "true")
    void snapshot_WhenRewriteRequested_IsWrittenFromCurrentOutput() throws IOException {
        StringBuilder file = new StringBuilder();
        for (ValidatorGoldenCorpus.ContentEntry entry : ValidatorGoldenCorpus.contentEntries()) {
            appendSection(file, entry.name(), outcomeOf(() -> contentValidator.validate(
                    entry.content(), entry.category(), entry.policy())));
        }
        for (ValidatorGoldenCorpus.VersionEntry entry : ValidatorGoldenCorpus.versionEntries()) {
            appendSection(file, entry.name(), outcomeOf(() -> versionValidator.validateVersionForCreate(
                    entry.plannerType(), entry.contentVersion())));
        }

        Files.createDirectories(REWRITE_TARGET.getParent());
        Files.writeString(REWRITE_TARGET, file.toString(), StandardCharsets.UTF_8);
    }

    private static final String OUTPUT_CHANGED =
            "frozen validator output changed for entry '%s' — a validator message or its ordering "
                    + "moved";

    private static final Map<String, ValidatorGoldenCorpus.ContentEntry> CONTENT_ENTRIES = byName(
            ValidatorGoldenCorpus.contentEntries(), ValidatorGoldenCorpus.ContentEntry::name);
    private static final Map<String, ValidatorGoldenCorpus.VersionEntry> VERSION_ENTRIES = byName(
            ValidatorGoldenCorpus.versionEntries(), ValidatorGoldenCorpus.VersionEntry::name);

    private static <T> Map<String, T> byName(List<T> entries, Function<T, String> name) {
        Map<String, T> byName = new LinkedHashMap<>();
        for (T entry : entries) {
            if (byName.put(name.apply(entry), entry) != null) {
                throw new IllegalStateException("duplicate corpus entry name: " + name.apply(entry));
            }
        }
        return byName;
    }

    private static Stream<String> contentEntryNames() {
        return CONTENT_ENTRIES.keySet().stream();
    }

    private static Stream<String> versionEntryNames() {
        return VERSION_ENTRIES.keySet().stream();
    }

    private static List<String> corpusNames() {
        List<String> names = new ArrayList<>(CONTENT_ENTRIES.keySet());
        names.addAll(VERSION_ENTRIES.keySet());
        return names;
    }

    /**
     * One entry's outcome, rendered so that every part of the contract is visible as text: the
     * code the client receives, the combined message, and each sub-error in accumulation order.
     */
    private static String outcomeOf(Runnable invocation) {
        try {
            invocation.run();
            return ACCEPTED;
        } catch (PlannerValidationException ex) {
            StringBuilder block = new StringBuilder()
                    .append("code: ").append(ex.getErrorCode()).append("\n")
                    .append("message: ").append(render(ex.getMessage()));
            int index = 1;
            for (ValidationError sub : ex.getSubErrors()) {
                block.append("\nsub ").append(index++).append(": ")
                        .append(sub.code()).append(" | ").append(render(sub.message()));
            }
            return block.toString();
        }
    }

    private static String render(String message) {
        if (message == null) {
            return "null";
        }
        return escape(normalizeSetOrder(elideParserDetail(message)));
    }

    private static final List<String> SET_RENDERED_PREFIXES =
            List.of("Missing required fields: ", "Unknown fields: ");

    /**
     * Sorts the elements of the two messages that render a {@link java.util.Set} directly.
     *
     * <p>The sets feeding them are built from {@code Set.of} constants, whose iteration order the
     * JVM salts per run, and the receiving {@code HashSet} resolves same-bucket ties by insertion
     * order — so {@code selectedKeywords}/{@code equipment} and sinner {@code 01}/{@code 12} can
     * each render in either order between runs. Sorting keeps that salt out of the snapshot, so
     * the gate answers about validator behaviour and a corpus author need not know which keys
     * collide.
     *
     * <p>Bracket depth is tracked rather than matched by regex: an element may carry brackets of
     * its own, as {@code equipment[05].egos.ZAYIN} does.
     */
    private static String normalizeSetOrder(String message) {
        String normalized = message;
        for (String prefix : SET_RENDERED_PREFIXES) {
            int found = normalized.indexOf(prefix);
            while (found >= 0) {
                int open = found + prefix.length();
                int close = closingBracket(normalized, open);
                if (close < 0) {
                    break;
                }
                String sorted = Arrays.stream(normalized.substring(open + 1, close).split(", ", -1))
                        .sorted()
                        .collect(Collectors.joining(", "));
                normalized = normalized.substring(0, open + 1) + sorted + normalized.substring(close);
                found = normalized.indexOf(prefix, open + sorted.length());
            }
        }
        return normalized;
    }

    private static int closingBracket(String text, int open) {
        if (open >= text.length() || text.charAt(open) != '[') {
            return -1;
        }
        int depth = 0;
        for (int at = open; at < text.length(); at++) {
            char character = text.charAt(at);
            if (character == '[') {
                depth++;
            } else if (character == ']' && --depth == 0) {
                return at;
            }
        }
        return -1;
    }

    private static final String PARSER_DETAIL = "Malformed JSON: <parser detail elided>";

    /**
     * Everything after the {@code Malformed JSON: } prefix on a parse failure is Jackson's prose,
     * down to its source-location clause, so freezing it would redden this gate on a parser
     * upgrade that moved no validator. The prefix and the code are the validator's contribution
     * and stay frozen; the details this validator writes itself carry no source clause and are
     * kept verbatim.
     */
    private static String elideParserDetail(String message) {
        return message.startsWith("Malformed JSON: ") && message.contains("[Source:")
                ? PARSER_DETAIL
                : message;
    }

    /** Parse messages carry newlines, which would otherwise split one section into many. */
    private static String escape(String message) {
        return message.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static void appendSection(StringBuilder file, String name, String outcome) {
        file.append(SECTION_MARKER).append(name).append("\n").append(outcome).append("\n\n");
    }

    private static Map<String, String> snapshot() {
        Map<String, String> sections = new LinkedHashMap<>();
        String name = null;
        List<String> body = new ArrayList<>();
        for (String line : readSnapshot().split("\n", -1)) {
            if (line.startsWith(SECTION_MARKER)) {
                closeSection(sections, name, body);
                name = line.substring(SECTION_MARKER.length());
                body = new ArrayList<>();
            } else if (name != null) {
                body.add(line);
            }
        }
        closeSection(sections, name, body);
        return sections;
    }

    /**
     * Drops the blank lines separating sections, and nothing else: a message may legitimately end
     * in a space — {@code Invalid category: } for a blank category — so trailing whitespace inside
     * a line is part of the frozen contract.
     */
    private static void closeSection(Map<String, String> sections, String name, List<String> body) {
        if (name == null) {
            return;
        }
        List<String> lines = new ArrayList<>(body);
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        sections.put(name, String.join("\n", lines));
    }

    private static String readSnapshot() {
        try (InputStream in = ValidatorGoldenCorpusTest.class.getResourceAsStream(SNAPSHOT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        SNAPSHOT_RESOURCE + " is absent; see this class's Javadoc to generate it");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + SNAPSHOT_RESOURCE, e);
        }
    }

    private static GameDataRegistry fixedRegistry(ObjectMapper objectMapper) {
        GameDataRegistry registry =
                new GameDataRegistry(new FixedGameDataLoader(objectMapper), "fixture");
        registry.refresh();
        return registry;
    }

    /**
     * The file-reading half of game data, answered from the corpus's fixed dataset. Substituting
     * at this seam and no higher leaves the id, enhancement-prefix and theme-pack rules to the
     * real registry, so an entry freezes what production answers rather than a second
     * implementation of it.
     */
    private static final class FixedGameDataLoader extends GameDataLoader {

        private FixedGameDataLoader(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public Set<String> loadKeysFromFile(Path filePath) {
            return switch (filePath.getFileName().toString()) {
                case "identitySpecList.json" -> ValidatorGoldenCorpus.IDENTITY_IDS;
                case "egoSpecList.json" -> ValidatorGoldenCorpus.EGO_IDS;
                case "egoGiftSpecList.json" -> ValidatorGoldenCorpus.EGO_GIFT_IDS;
                case "themePackList.json" -> ValidatorGoldenCorpus.THEME_PACK_IDS;
                case "startBuffs.json" -> ValidatorGoldenCorpus.START_BUFF_IDS;
                default -> throw new IllegalStateException("no fixture for " + filePath);
            };
        }

        @Override
        public Map<String, Set<String>> loadStartGiftPools(Path filePath) {
            return ValidatorGoldenCorpus.START_GIFT_POOLS;
        }

        @Override
        public Map<String, List<String>> loadEgoGiftThemePackMap(Path filePath) {
            return ValidatorGoldenCorpus.EGO_GIFT_THEME_PACKS;
        }

        @Override
        public Map<String, Integer> loadEgoMaxThreadspin(Path filePath) {
            return ValidatorGoldenCorpus.EGO_MAX_THREADSPIN;
        }
    }
}
