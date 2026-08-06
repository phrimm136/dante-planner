package org.danteplanner.backend.planner.validation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * <p>Game data is answered from fixed sets rather than the static JSON tree, so the snapshot
 * moves only when a validator does. Regenerate it deliberately with
 * {@code GOLDEN_CORPUS_REWRITE=true} and read the resulting diff: every changed line is a
 * changed contract.
 */
class ValidatorGoldenCorpusTest {

    private static final String SNAPSHOT_RESOURCE = "/validation/golden-corpus.txt";
    private static final Path SNAPSHOT_SOURCE = Path.of("src/test/resources/validation/golden-corpus.txt");

    private static final String SECTION_MARKER = "### ";
    private static final String ACCEPTED = "accepted";

    private ObjectMapper objectMapper;
    private PlannerContentValidator contentValidator;
    private IdReferenceValidator idReferenceValidator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        GameDataRegistry registry = new FixedGameDataRegistry();
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
                .as(REGENERATION_HINT, name)
                .isEqualTo(snapshot().get(name));
    }

    @Test
    @DisplayName("the snapshot holds exactly the corpus, no more and no less")
    void snapshot_WhenComparedToTheCorpus_CoversEveryEntryExactlyOnce() {
        assertThat(snapshot().keySet())
                .as("a section with no entry is a frozen message nothing produces any more, and an "
                        + "entry with no section is an unfrozen one; regenerate with "
                        + "GOLDEN_CORPUS_REWRITE=true and read the diff")
                .containsExactlyElementsOf(corpusNames());
    }

    /**
     * Writes the snapshot from the current output of the chain. Disabled unless asked for: a gate
     * that repairs itself on every run gates nothing.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "GOLDEN_CORPUS_REWRITE", matches = "true")
    void snapshot_WhenRewriteRequested_IsWrittenFromCurrentOutput() throws IOException {
        StringBuilder file = new StringBuilder();
        for (ValidatorGoldenCorpus.ContentEntry entry : ValidatorGoldenCorpus.contentEntries()) {
            appendSection(file, entry.name(), outcomeOf(() -> contentValidator.validate(
                    entry.content(), entry.category(), entry.policy())));
        }
        Files.createDirectories(SNAPSHOT_SOURCE.getParent());
        Files.writeString(SNAPSHOT_SOURCE, file.toString(), StandardCharsets.UTF_8);
    }

    private static final String REGENERATION_HINT =
            "corpus entry '%s' no longer produces the frozen output; if the change is intended, "
                    + "regenerate with GOLDEN_CORPUS_REWRITE=true and justify every changed line";

    private static final Map<String, ValidatorGoldenCorpus.ContentEntry> CONTENT_ENTRIES = byName(
            ValidatorGoldenCorpus.contentEntries(), ValidatorGoldenCorpus.ContentEntry::name);
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

    private static List<String> corpusNames() {
        return new ArrayList<>(CONTENT_ENTRIES.keySet());
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
                    .append("message: ").append(escape(ex.getMessage()));
            int index = 1;
            for (ValidationError sub : ex.getSubErrors()) {
                block.append("\nsub ").append(index++).append(": ")
                        .append(sub.code()).append(" | ").append(escape(sub.message()));
            }
            return block.toString();
        }
    }

    /** Jackson's parse messages carry newlines, which would otherwise split one section into many. */
    private static String escape(String message) {
        if (message == null) {
            return "null";
        }
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
                throw new IllegalStateException(SNAPSHOT_RESOURCE
                        + " is absent; generate it with GOLDEN_CORPUS_REWRITE=true");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + SNAPSHOT_RESOURCE, e);
        }
    }

    /**
     * Game data as a fixed set of answers. Every query the validators make is answered here, so
     * the loader the superclass would read the static JSON tree with is never reached.
     */
    private static final class FixedGameDataRegistry extends GameDataRegistry {

        private FixedGameDataRegistry() {
            super(null, "");
        }

        @Override
        public boolean hasIdentity(String id) {
            return ValidatorGoldenCorpus.IDENTITY_IDS.contains(id);
        }

        @Override
        public boolean hasEgo(String id) {
            return ValidatorGoldenCorpus.EGO_IDS.contains(id);
        }

        @Override
        public Integer getEgoMaxThreadspin(String id) {
            return ValidatorGoldenCorpus.EGO_MAX_THREADSPIN.get(id);
        }

        @Override
        public boolean hasEgoGift(String id) {
            return ValidatorGoldenCorpus.EGO_GIFT_IDS.contains(stripEnhancement(id));
        }

        @Override
        public boolean hasThemePack(String id) {
            return ValidatorGoldenCorpus.THEME_PACK_IDS.contains(id);
        }

        @Override
        public boolean hasStartBuff(String id) {
            return ValidatorGoldenCorpus.START_BUFF_IDS.contains(id);
        }

        @Override
        public Set<String> getStartGiftPool(String keyword) {
            return ValidatorGoldenCorpus.START_GIFT_POOLS.get(keyword);
        }

        @Override
        public boolean hasStartGiftKeyword(String keyword) {
            return ValidatorGoldenCorpus.START_GIFT_POOLS.containsKey(keyword);
        }

        @Override
        public boolean isGiftAffordableForThemePack(String giftId, String themePackId) {
            String baseId = stripEnhancement(giftId);
            return ValidatorGoldenCorpus.EGO_GIFT_IDS.contains(baseId)
                    && !ValidatorGoldenCorpus.UNAFFORDABLE_GIFT_IDS.contains(baseId);
        }

        /** Mirrors the registry's enhancement-prefix rule, which the corpus exercises directly. */
        private static final Pattern GIFT_ENHANCEMENT = Pattern.compile("^[12]?(9\\d{3})$");

        private static String stripEnhancement(String id) {
            if (id == null) {
                return null;
            }
            Matcher matcher = GIFT_ENHANCEMENT.matcher(id);
            return matcher.matches() ? matcher.group(1) : id;
        }
    }
}
