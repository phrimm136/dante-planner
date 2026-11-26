package org.danteplanner.backend.shared.freshness;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.danteplanner.backend.shared.config.ChosenReadSource;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.exception.ReadTemporarilyUnavailableException;
import org.danteplanner.backend.shared.failure.CommandOutcome;
import org.danteplanner.backend.shared.freshness.MarkerStore.MarkerView;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;

public class FreshnessReadGuard {

    public static final String READ_WAIT = "freshness.read.wait";
    public static final String SOURCE_TAG = "source";
    public static final String OUTCOME_TAG = "outcome";
    public static final String APPLIED = "applied";
    public static final String TIMEOUT = "timeout";

    private static final String NO_GTIDS = "";

    public sealed interface SettleOutcome extends CommandOutcome {

        record Serve() implements SettleOutcome {
        }

        record Skip(ReadSourceSelector.Source source) implements SettleOutcome {
        }

        record Reload() implements SettleOutcome {
        }
    }

    public sealed interface Required {

        record None() implements Required {
        }

        record Unknown() implements Required {
        }

        record AtLeast(String gtidSet) implements Required {
        }

        static Required of(Collection<MarkerView> views) {
            if (views.stream().anyMatch(MarkerView.Unknown.class::isInstance)) {
                return new Unknown();
            }
            Set<String> marked = views.stream()
                    .filter(MarkerView.At.class::isInstance)
                    .map(view -> ((MarkerView.At) view).gtidSet())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (marked.isEmpty()) {
                return new None();
            }
            return new AtLeast(GtidWriteCapture.unionGtidSets(marked));
        }
    }

    public sealed interface Verdict {

        record Serve() implements Verdict {
        }

        record NextSource() implements Verdict {
        }

        record WaitFor(String gtids) implements Verdict {
        }
    }

    private enum Waited {
        APPLIED,
        TIMED_OUT;

        static Waited of(boolean applied) {
            return applied ? APPLIED : TIMED_OUT;
        }
    }

    @FunctionalInterface
    public interface MarkerReads {
        Map<FreshnessScope, MarkerView> read(Collection<FreshnessScope> scopes);
    }

    @FunctionalInterface
    public interface Attempt {
        ScopedRead<?> run() throws Throwable;
    }

    @FunctionalInterface
    public interface GtidWait {
        boolean isApplied(ReadSourceSelector.Source source, String gtidSet, Duration bound);
    }

    private final MarkerReads markers;
    private final GtidWait gtidWait;
    private final Duration waitBound;
    private final MeterRegistry meterRegistry;
    private final ReadSourceSelector selector;
    private final Map<String, String> appliedBySource = new ConcurrentHashMap<>();

    public FreshnessReadGuard(MarkerReads markers, GtidWait gtidWait, FreshnessReadSettings settings,
            MeterRegistry meterRegistry, ReadSourceSelector selector) {
        this.markers = markers;
        this.gtidWait = gtidWait;
        this.waitBound = settings.waitBound();
        this.meterRegistry = meterRegistry;
        this.selector = selector;
    }

    public ScopedRead<?> read(Attempt attempt) throws Throwable {
        Set<String> reloaded = new HashSet<>();
        while (true) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("a freshness read must run outside a transaction");
            }
            ChosenReadSource.clear();
            Map<String, String> appliedAtLoadStart = applied();
            ScopedRead<?> read;
            try {
                read = attempt.run();
            } catch (ScopedMiss miss) {
                if (settleOnce(miss.scopes(), appliedAtLoadStart, reloaded) instanceof SettleOutcome.Serve) {
                    throw miss;
                }
                continue;
            }
            if (settleOnce(read.scopes(), appliedAtLoadStart, reloaded) instanceof SettleOutcome.Serve) {
                return read;
            }
        }
    }

    private SettleOutcome settleOnce(Collection<FreshnessScope> scopes, Map<String, String> appliedAtLoadStart,
            Set<String> reloaded) {
        ReadSourceSelector.Source source = servedSource();
        return switch (settle(scopes, source, appliedAtLoadStart, reloaded)) {
            case SettleOutcome.Reload reload -> {
                reloaded.add(source.name());
                yield reload;
            }
            case SettleOutcome.Serve serve -> serve;
            case SettleOutcome.Skip skip -> skip;
        };
    }

    private ReadSourceSelector.Source servedSource() {
        return ChosenReadSource.source().fold(Function.identity(),
                () -> selector.current().orElseThrow(ReadTemporarilyUnavailableException::new));
    }

    public Map<String, String> applied() {
        return Map.copyOf(appliedBySource);
    }

    public SettleOutcome settle(Collection<FreshnessScope> scopes, ReadSourceSelector.Source source,
            Map<String, String> appliedAtLoadStart, Set<String> reloadedSources) {
        String appliedAtStart = appliedAtLoadStart.getOrDefault(source.name(), NO_GTIDS);
        return switch (verdict(Required.of(viewsFor(scopes, source)), source, appliedAtStart, reloadedSources)) {
            case Verdict.Serve serve -> new SettleOutcome.Serve();
            case Verdict.NextSource next -> skip(source);
            case Verdict.WaitFor wait -> switch (Waited.of(await(source, wait.gtids()))) {
                case APPLIED -> new SettleOutcome.Reload();
                case TIMED_OUT -> skip(source);
            };
        };
    }

    public static Verdict verdict(Required required, ReadSourceSelector.Source source, String appliedAtStart,
            Set<String> reloadedSources) {
        return switch (source.authority()) {
            case PRIMARY -> new Verdict.Serve();
            case REPLICA -> switch (required) {
                case Required.None none -> new Verdict.Serve();
                case Required.Unknown unknown -> new Verdict.NextSource();
                case Required.AtLeast atLeast when contains(appliedAtStart, atLeast.gtidSet()) -> new Verdict.Serve();
                case Required.AtLeast atLeast when !reloadedSources.contains(source.name()) ->
                        new Verdict.WaitFor(atLeast.gtidSet());
                case Required.AtLeast atLeast -> new Verdict.NextSource();
            };
        };
    }

    private Collection<MarkerView> viewsFor(Collection<FreshnessScope> scopes, ReadSourceSelector.Source source) {
        return switch (source.authority()) {
            case PRIMARY -> List.of();
            case REPLICA -> markers.read(scopes).values();
        };
    }

    private static boolean contains(String applied, String required) {
        return union(applied, required).equals(applied);
    }

    private boolean await(ReadSourceSelector.Source source, String required) {
        long start = System.nanoTime();
        boolean applied = gtidWait.isApplied(source, required, waitBound);
        Timer.builder(READ_WAIT)
                .tag(SOURCE_TAG, source.name())
                .tag(OUTCOME_TAG, applied ? APPLIED : TIMEOUT)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Duration.ofNanos(System.nanoTime() - start));
        if (applied) {
            appliedBySource.merge(source.name(), required, FreshnessReadGuard::union);
        }
        return applied;
    }

    private static SettleOutcome skip(ReadSourceSelector.Source source) {
        ReadSourceSelector.skip(source);
        return new SettleOutcome.Skip(source);
    }

    private static String union(String left, String right) {
        return GtidWriteCapture.unionGtidSets(new LinkedHashSet<>(List.of(left, right)));
    }
}
