package org.danteplanner.backend.planner.floor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import net.jqwik.api.lifecycle.LifecycleContext;
import net.jqwik.api.lifecycle.SkipExecutionHook;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.validation.FloorRuleTable;
import org.danteplanner.backend.planner.validation.GameDataLoader;
import org.danteplanner.backend.planner.validation.GameDataRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FloorRulesReplayTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final FloorRuleTable TABLE =
            new GameDataLoader(MAPPER).loadFloorRules(Path.of("../static/data/plannerFloorRules.json"));
    private static final FloorRules RULES = rulesOver(TABLE);
    private static final String EXCHANGE_OUT = "FLOOR_EXCHANGE_OUT";
    private static final String EXCHANGE_IN = "FLOOR_EXCHANGE_IN";
    private static final String COMMITTED_EXCHANGE = "../testdata/floor-exchange-frontend.json";
    private static final long EXCHANGE_SEED = 20260928L;
    private static final int EXCHANGE_CASES = 200;
    private static final int GENERATION_SIZE = 1000;

    static final class RequiresExchangeOut implements SkipExecutionHook {

        @Override
        public SkipResult shouldBeSkipped(LifecycleContext context) {
            String out = System.getenv(EXCHANGE_OUT);
            return out == null || out.isEmpty() ? SkipResult.skip(EXCHANGE_OUT + " is unset") : SkipResult.doNotSkip();
        }
    }

    @Example
    @AddLifecycleHook(RequiresExchangeOut.class)
    void exchange_WhenAnOutputPathIsGiven_WritesTheGeneratedCasesWithTheirOutcomes() throws IOException {
        Path out = Path.of(System.getenv(EXCHANGE_OUT));
        Random random = new Random(EXCHANGE_SEED);
        var generator = FloorArbitraries.rawCase(TABLE).generator(GENERATION_SIZE);
        ArrayNode cases = MAPPER.createArrayNode();
        for (int index = 0; index < EXCHANGE_CASES; index++) {
            FloorArbitraries.RawCase rawCase = generator.next(random).value();
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
    void replay_WhenTheFrontendsCasesAreRead_ReproducesEveryRecordedOutcome() throws IOException {
        String override = System.getenv(EXCHANGE_IN);
        Path in = Path.of(override == null || override.isEmpty() ? COMMITTED_EXCHANGE : override);
        JsonNode exchange = MAPPER.readTree(in.toFile());
        List<String> mismatches = new ArrayList<>();
        JsonNode cases = exchange.path("cases");
        for (int index = 0; index < cases.size(); index++) {
            JsonNode exchangeCase = cases.get(index);
            JsonNode actual = run(exchangeCase.path("raw"),
                    MDCategory.fromValue(exchangeCase.path("category").asText()),
                    Stage.fromTableName(exchangeCase.path("stage").asText()));
            if (!actual.equals(exchangeCase.path("outcome"))) {
                mismatches.add("case " + index + ": " + exchangeCase + " actual=" + actual);
            }
        }

        assertThat(exchange.path("producer").asText()).isEqualTo("frontend");
        assertThat(cases.size()).isPositive();
        assertThat(mismatches).isEmpty();
    }

    private static JsonNode run(JsonNode raw, MDCategory category, Stage stage) {
        return outcome(RULES.admit(FloorBoundary.parse(raw, TABLE.floorCount(category)), category, stage));
    }

    private static JsonNode outcome(Admission admission) {
        ObjectNode outcome = MAPPER.createObjectNode();
        ArrayNode floors = MAPPER.createArrayNode();
        List<Violation> violations = switch (admission) {
            case Admission.Admitted admitted -> {
                admitted.floors().forEach(floor -> floors.add(floorOutcome(floor)));
                yield admitted.boundaryViolations();
            }
            case Admission.Rejected rejected -> rejected.violations();
        };
        outcome.put("ok", admission instanceof Admission.Admitted);
        outcome.set("floors", floors);
        ArrayNode violationNodes = outcome.putArray("violations");
        violations.forEach(violation -> violationNodes.addObject()
                .put("code", violation.code().getCode())
                .put("path", violation.path()));
        return outcome;
    }

    private static JsonNode floorOutcome(FloorSelection floor) {
        ObjectNode node = MAPPER.createObjectNode();
        if (floor.themePack() instanceof ThemePack.Chosen(String id)) {
            node.put("themePackId", id);
        } else {
            node.putNull("themePackId");
        }
        if (floor.difficulty() instanceof Difficulty.Set(int value)) {
            node.put("difficulty", value);
        } else {
            node.putNull("difficulty");
        }
        floor.giftIds().forEach(node.putArray("giftIds")::add);
        return node;
    }

    private static FloorRules rulesOver(FloorRuleTable table) {
        GameDataRegistry registry = mock(GameDataRegistry.class);
        when(registry.floorRules()).thenReturn(table);
        return new FloorRules(registry);
    }
}
