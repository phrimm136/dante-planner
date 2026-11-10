package org.danteplanner.backend.planner.validation.floor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.validation.Violation;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.MAPPER;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.TABLE;

class FloorRulesReplayTest {

    private static final FloorRules RULES = new FloorRules(TABLE);
    private static final String EXCHANGE_OUT = "FLOOR_EXCHANGE_OUT";
    private static final String EXCHANGE_IN = "FLOOR_EXCHANGE_IN";
    private static final String COMMITTED_EXCHANGE = "../testdata/floor-exchange-frontend.json";
    private static final long EXCHANGE_SEED = 20260928L;
    private static final int EXCHANGE_CASES = 200;

    @Test
    @EnabledIfEnvironmentVariable(named = EXCHANGE_OUT, matches = ".+")
    void exchange_WhenAnOutputPathIsGiven_WritesTheGeneratedCasesWithTheirOutcomes() throws IOException {
        Path out = Path.of(System.getenv(EXCHANGE_OUT));
        ArrayNode cases = MAPPER.createArrayNode();
        for (FloorGenerators.RawCase rawCase : generateCases(EXCHANGE_SEED, EXCHANGE_CASES)) {
            ObjectNode exchangeCase = cases.addObject();
            exchangeCase.set("raw", rawCase.raw());
            exchangeCase.put("category", rawCase.category().getValue());
            exchangeCase.put("stage", rawCase.stage().tableName());
            exchangeCase.set("outcome", run(rawCase.raw(), rawCase.category(), rawCase.stage()));
        }
        ObjectNode exchange = MAPPER.createObjectNode().put("producer", "backend");
        exchange.set("cases", cases);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), exchange);

        assertThat(MAPPER.readTree(out.toFile()).path("cases").size()).isEqualTo(EXCHANGE_CASES);
    }

    @Test
    void exchange_WhenTheSeedIsFixed_GeneratesTheSameCasesTwice() {
        List<FloorGenerators.RawCase> first = generateCases(EXCHANGE_SEED, EXCHANGE_CASES);
        List<FloorGenerators.RawCase> second = generateCases(EXCHANGE_SEED, EXCHANGE_CASES);

        assertThat(first).hasSize(EXCHANGE_CASES);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void replay_WhenTheFrontendsCasesAreRead_ReproducesEveryRecordedOutcome() throws IOException {
        String override = System.getenv(EXCHANGE_IN);
        Path in = Path.of(override == null || override.isEmpty() ? COMMITTED_EXCHANGE : override);
        JsonNode exchange = MAPPER.readTree(in.toFile());
        List<String> mismatches = new ArrayList<>();
        JsonNode cases = exchange.path("cases");
        for (int index = 0; index < cases.size(); index++) {
            JsonNode exchangeCase = cases.get(index);
            JsonNode actual = run(exchangeCase.path("raw"),
                    MDCategory.fromValue(exchangeCase.path("category").asString()),
                    Stage.fromTableName(exchangeCase.path("stage").asString()));
            if (!actual.equals(exchangeCase.path("outcome"))) {
                mismatches.add("case " + index + ": " + exchangeCase + " actual=" + actual);
            }
        }

        assertThat(exchange.path("producer").asString()).isEqualTo("frontend");
        assertThat(cases.size()).isPositive();
        assertThat(mismatches).isEmpty();
    }

    private static List<FloorGenerators.RawCase> generateCases(long seed, int count) {
        List<FloorGenerators.RawCase> cases = new ArrayList<>();
        PropertyChecker.customized().withSeed(seed).withIterationCount(count).silent()
                .forAll(FloorGenerators.rawCase(TABLE), rawCase -> {
                    cases.add(rawCase);
                    return true;
                });
        return cases;
    }

    private static JsonNode run(JsonNode raw, MDCategory category, Stage stage) {
        return outcome(RULES.admit(FloorFixtures.parse(raw, category), stage));
    }

    private static JsonNode outcome(Admission admission) {
        ObjectNode outcome = MAPPER.createObjectNode();
        List<Violation> violations = switch (admission) {
            case Admission.Admitted admitted -> {
                outcome.put("ok", true);
                outcome.set("floors", FloorFixtures.outcomeFloors(admitted.floors()));
                yield admitted.boundaryViolations();
            }
            case Admission.Rejected rejected -> {
                outcome.put("ok", false);
                outcome.set("floors", MAPPER.createArrayNode());
                yield rejected.violations();
            }
        };
        ArrayNode violationNodes = outcome.putArray("violations");
        violations.forEach(violation -> violationNodes.addObject()
                .put("code", violation.code().getCode())
                .put("path", violation.path().render()));
        return outcome;
    }
}
