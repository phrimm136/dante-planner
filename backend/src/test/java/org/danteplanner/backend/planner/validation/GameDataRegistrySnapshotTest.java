package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The registry answers a validation from one load of the game data, never from two.
 *
 * <p>A planner save reads identities, EGOs, gifts, theme packs and buffs one lookup at a time
 * while a reload may be running. Data loaded together is consistent; data mixed across loads is
 * not, and the rejection it produces names an id that exists.</p>
 *
 * <p>Every structure here carries the number of the load that built it, so a mix is visible to a
 * reader as two numbers rather than as a rejection nobody can reproduce. The reader reads that
 * number before and after a round of lookups and judges only the rounds no load fell inside —
 * loads are numbered upward, so equal numbers mean one load answered the whole round.</p>
 */
class GameDataRegistrySnapshotTest {

    private static final String GENERATION_KEY = "generation";
    private static final int FIRST_GENERATION = 1;
    private static final int GENERATIONS = 50_000;
    private static final int READ_ROUNDS = 20_000;
    private static final long JOIN_TIMEOUT_MS = 60_000;

    @Test
    @DisplayName("a lookup round spanning a reload sees one load's data or the other's, never both")
    void read_WhenARefreshRunsConcurrently_ObservesOneGeneration() throws InterruptedException {
        GenerationalGameDataLoader loader = new GenerationalGameDataLoader();
        GameDataRegistry registry = new GameDataRegistry(loader, "generational");
        loader.generation = FIRST_GENERATION;
        registry.refresh();

        AtomicBoolean reading = new AtomicBoolean(true);
        Thread reloads = new Thread(() -> {
            for (int generation = FIRST_GENERATION + 1;
                    generation <= GENERATIONS && reading.get();
                    generation++) {
                loader.generation = generation;
                registry.refresh();
            }
        }, "game-data-reloader");
        reloads.setDaemon(true);
        reloads.start();

        int judged = 0;
        for (int round = 0; round < READ_ROUNDS; round++) {
            int before = registry.getEgoMaxThreadspin(GENERATION_KEY);
            boolean wholeGeneration = observesWholeGeneration(registry, before);
            int after = registry.getEgoMaxThreadspin(GENERATION_KEY);

            if (before != after) {
                continue;
            }
            judged++;
            assertThat(wholeGeneration)
                    .as("generation %d answered part of the round and another load answered the rest",
                            before)
                    .isTrue();
        }

        reading.set(false);
        reloads.join(JOIN_TIMEOUT_MS);
        assertThat(reloads.isAlive()).as("the reloads finished").isFalse();
        assertThat(judged).as("no round was judged, so the assertion above never ran").isPositive();
    }

    /**
     * Every read path of the registry, asked for the data of one generation.
     *
     * @param registry   the registry under read
     * @param generation the generation the round is judged against
     * @return true when all eight structures carry that generation
     */
    private static boolean observesWholeGeneration(GameDataRegistry registry, int generation) {
        return registry.hasIdentity("identity-" + generation)
                && registry.hasEgo("ego-" + generation)
                && registry.hasEgoGift("gift-" + generation)
                && registry.hasThemePack("pack-" + generation)
                && registry.hasStartBuff("buff-" + generation)
                && registry.hasStartGiftKeyword("keyword-" + generation)
                && registry.getStartGiftPool("keyword-" + generation) != null
                && registry.isGiftAffordableForThemePack("themed-gift-" + generation, "pack-" + generation)
                && registry.isPopulated();
    }

    /**
     * The file-reading half of game data, answering with a dataset that names the load it belongs
     * to. Substituting at this seam leaves the registry's own publication to the class under test.
     */
    private static final class GenerationalGameDataLoader extends GameDataLoader {

        private int generation;

        private GenerationalGameDataLoader() {
            super(new ObjectMapper());
        }

        @Override
        public Set<String> loadKeysFromFile(Path filePath) {
            return switch (filePath.getFileName().toString()) {
                case "identitySpecList.json" -> Set.of("identity-" + generation);
                case "egoSpecList.json" -> Set.of("ego-" + generation);
                case "egoGiftSpecList.json" -> Set.of("gift-" + generation);
                case "themePackList.json" -> Set.of("pack-" + generation);
                case "startBuffs.json" -> Set.of("buff-" + generation);
                default -> throw new IllegalStateException("no fixture for " + filePath);
            };
        }

        @Override
        public Map<String, Set<String>> loadStartGiftPools(Path filePath) {
            return Map.of("keyword-" + generation, Set.of("pool-gift-" + generation));
        }

        @Override
        public Map<String, List<String>> loadEgoGiftThemePackMap(Path filePath) {
            return Map.of("themed-gift-" + generation, List.of("pack-" + generation));
        }

        @Override
        public Map<String, Integer> loadEgoMaxThreadspin(Path filePath) {
            return Map.of(GENERATION_KEY, generation);
        }
    }
}
