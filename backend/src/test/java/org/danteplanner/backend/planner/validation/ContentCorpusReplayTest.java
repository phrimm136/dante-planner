package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.gamedata.FloorRuleTable;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.gamedata.PlannerVersions;
import org.danteplanner.backend.planner.precondition.PlannerPublishValidator;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.planner.validation.floor.Stage;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class ContentCorpusReplayTest {

    private static final Path CORPUS_FILE = Path.of("../testdata/planner-content.corpus.json");
    private static final String OUT = "CONTENT_CORPUS_OUT";
    private static final String DEFAULT_TITLE = "Corpus";
    private static final int MAX_CONTENT_SIZE_BYTES = 51200;
    private static final int MAX_NOTE_SIZE_BYTES = 1024;
    private static final List<String> DEFAULT_STAGES = List.of("draft", "publish");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNode CORPUS = readCorpus();
    private static final JsonNode GAME_DATA = CORPUS.path("gameData");
    private static final int CONTENT_VERSION = GAME_DATA.path("contentVersion").asInt();

    private final GameDataRegistry registry = registry();
    private final PlannerContentValidator validator = new PlannerContentValidator(MAPPER, MAX_CONTENT_SIZE_BYTES,
            MAX_NOTE_SIZE_BYTES, registry, new SinnerIdValidator(), new FloorRules(registry));
    private final PlannerPublishValidator publishValidator = new PlannerPublishValidator();

    static Stream<Arguments> caseStages() {
        return cases().flatMap(corpusCase -> stagesOf(corpusCase).stream()
                .map(stage -> Arguments.of(corpusCase.path("name").asText(), stage, corpusCase)));
    }

    @ParameterizedTest(name = "{0} at {1}")
    @MethodSource("caseStages")
    void corpusCase_WhenReplayedAtItsStage_ReturnsTheExpectedCodesAndPathsInOrder(String name, String stage,
                                                                                JsonNode corpusCase) {
        JsonNode expected = corpusCase.path("expected").path(stage);

        assertThat(expected.isArray()).as("%s has an expected list for %s", name, stage).isTrue();
        assertThat(pairs(outcome(corpusCase, stage).path("violations"))).isEqualTo(pairs(expected));
    }

    @Test
    void corpus_WhenEveryCaseIsReplayed_RecordsAnOutcomePerStage() {
        ArrayNode results = results();

        assertThat(results.size()).isEqualTo(expectedResultCount());
        assertThat(results.findValues("outcome")).allSatisfy(outcome -> assertThat(outcome.isObject()).isTrue());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = OUT, matches = ".+")
    void corpus_WhenAnOutputPathIsGiven_WritesTheOrderedCodesAndPaths() throws IOException {
        ObjectNode file = MAPPER.createObjectNode().put("producer", "backend");
        file.set("results", results());
        Path out = Path.of(System.getenv(OUT));
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), file);

        assertThat(MAPPER.readTree(out.toFile()).path("results").size()).isEqualTo(expectedResultCount());
    }

    private ArrayNode results() {
        ArrayNode results = MAPPER.createArrayNode();
        cases().forEach(corpusCase -> stagesOf(corpusCase).forEach(stage -> {
            ObjectNode result = results.addObject()
                    .put("name", corpusCase.path("name").asText())
                    .put("stage", stage);
            result.set("outcome", outcome(corpusCase, stage));
        }));
        return results;
    }

    private ObjectNode outcome(JsonNode corpusCase, String stage) {
        String content = contentOf(corpusCase);
        String category = corpusCase.path("category").asText();
        int version = corpusCase.has("version") ? corpusCase.path("version").asInt() : CONTENT_VERSION;
        Stage validatorStage = "publish".equals(stage) ? Stage.PUBLISH : Stage.DRAFT;
        ObjectNode outcome = MAPPER.createObjectNode();
        try {
            if (validatorStage == Stage.PUBLISH) {
                publishValidator.requireTitle(titleOf(corpusCase));
            }
            validator.parse(content, MDCategory.fromValue(category), validator.season(version), validatorStage);
            outcome.putArray("violations");
        } catch (PlannerValidationException rejected) {
            ArrayNode violations = outcome.putArray("violations");
            if (rejected.getSubErrors().isEmpty()) {
                violations.addObject().put("code", rejected.getOriginalCode()).put("path", "");
            }
            rejected.getSubErrors().forEach(sub -> violations.addObject()
                    .put("code", sub.code())
                    .put("path", sub.path()));
        } catch (RuntimeException threw) {
            outcome.put("threw", threw.getClass().getSimpleName() + ": " + threw.getMessage());
        }
        return outcome;
    }

    private static List<List<String>> pairs(JsonNode violations) {
        return StreamSupport.stream(violations.spliterator(), false)
                .map(violation -> List.of(violation.path("code").asText(), violation.path("path").asText()))
                .toList();
    }

    private static String titleOf(JsonNode corpusCase) {
        JsonNode title = corpusCase.path("title");
        return switch (title.getNodeType()) {
            case MISSING -> DEFAULT_TITLE;
            case NULL -> null;
            default -> title.asText();
        };
    }

    private static String contentOf(JsonNode corpusCase) {
        if (corpusCase.has("raw")) {
            return corpusCase.path("raw").asText();
        }
        ObjectNode content = CORPUS.path("base").deepCopy();
        corpusCase.path("edits").forEach(edit -> apply(content, edit));
        return content.toString();
    }

    private static void apply(ObjectNode content, JsonNode edit) {
        List<JsonNode> at = StreamSupport.stream(edit.path("at").spliterator(), false).toList();
        JsonNode parent = at.subList(0, at.size() - 1).stream()
                .reduce((JsonNode) content, ContentCorpusReplayTest::child, (left, right) -> right);
        JsonNode last = at.get(at.size() - 1);
        boolean remove = edit.path("remove").asBoolean(false);
        if (parent instanceof ArrayNode array) {
            if (remove) {
                array.remove(last.asInt());
            } else {
                array.set(last.asInt(), edit.path("value").deepCopy());
            }
        } else if (remove) {
            ((ObjectNode) parent).remove(last.asText());
        } else {
            ((ObjectNode) parent).set(last.asText(), edit.path("value").deepCopy());
        }
    }

    private static JsonNode child(JsonNode node, JsonNode segment) {
        return segment.isNumber() ? node.get(segment.asInt()) : node.get(segment.asText());
    }

    private static Stream<JsonNode> cases() {
        return StreamSupport.stream(CORPUS.path("cases").spliterator(), false);
    }

    private static List<String> stagesOf(JsonNode corpusCase) {
        return corpusCase.has("stages")
                ? StreamSupport.stream(corpusCase.path("stages").spliterator(), false).map(JsonNode::asText).toList()
                : DEFAULT_STAGES;
    }

    private static int expectedResultCount() {
        return cases().mapToInt(corpusCase -> stagesOf(corpusCase).size()).sum();
    }

    private static JsonNode readCorpus() {
        try {
            return MAPPER.readTree(CORPUS_FILE.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + CORPUS_FILE, e);
        }
    }

    private static Set<String> strings(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static GameDataRegistry registry() {
        GameDataRegistry registry = new GameDataRegistry(new CorpusGameDataLoader(), "corpus");
        registry.refresh();
        return registry;
    }

    private static final class CorpusGameDataLoader extends GameDataLoader {

        private CorpusGameDataLoader() {
            super(MAPPER);
        }

        @Override
        public Set<String> loadKeysFromFile(Path filePath) {
            return switch (filePath.getFileName().toString()) {
                case "identitySpecList.json" -> strings(GAME_DATA.path("identityIds"));
                case "egoSpecList.json" -> strings(GAME_DATA.path("egoIds"));
                case "egoGiftSpecList.json" -> strings(GAME_DATA.path("egoGiftIds"));
                case "themePackList.json" -> strings(GAME_DATA.path("themePackIds"));
                case "startBuffs.json" -> strings(GAME_DATA.path("startBuffIds"));
                default -> throw new IllegalStateException("no corpus game data for " + filePath);
            };
        }

        @Override
        public PlannerVersions loadPlannerVersions(Path filePath) {
            return new PlannerVersions(2, List.of(CONTENT_VERSION), List.of(1, 5));
        }

        @Override
        public FloorRuleTable loadFloorRules(Path filePath) {
            return super.loadFloorRules(Path.of("../static/data", GameDataRegistry.FLOOR_RULES_FILE));
        }

        @Override
        public Path seasonDirectory(Path dataDir, int version) {
            return dataDir.resolve(SEASON_DIRECTORY_PREFIX + version);
        }

        @Override
        public Map<String, Set<String>> loadStartGiftPools(Path filePath) {
            Map<String, Set<String>> pools = new LinkedHashMap<>();
            GAME_DATA.path("startGiftPools").properties()
                    .forEach(pool -> pools.put(pool.getKey(), strings(pool.getValue())));
            return pools;
        }

        @Override
        public Map<String, List<String>> loadEgoGiftThemePackMap(Path filePath) {
            Map<String, List<String>> packs = new LinkedHashMap<>();
            GAME_DATA.path("egoGiftThemePacks").properties()
                    .forEach(gift -> packs.put(gift.getKey(), List.copyOf(strings(gift.getValue()))));
            return packs;
        }

        @Override
        public Map<String, Integer> loadEgoMaxThreadspin(Path filePath) {
            Map<String, Integer> ceilings = new LinkedHashMap<>();
            GAME_DATA.path("egoMaxThreadspin").properties()
                    .forEach(ego -> ceilings.put(ego.getKey(), ego.getValue().asInt()));
            return ceilings;
        }
    }
}
