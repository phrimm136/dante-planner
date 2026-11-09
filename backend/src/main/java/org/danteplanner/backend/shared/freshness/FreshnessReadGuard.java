package org.danteplanner.backend.shared.freshness;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.exception.ReadTemporarilyUnavailableException;
import org.danteplanner.backend.shared.freshness.MarkerStore.MarkerView;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.shared.readpath.ChosenReadSource;

public class FreshnessReadGuard {

    public static final String READ_WAIT = "freshness.read.wait";
    public static final String SOURCE_TAG = "source";
    public static final String OUTCOME_TAG = "outcome";
    public static final String APPLIED = "applied";
    public static final String TIMEOUT = "timeout";

    public sealed interface Decision {

        record Serve() implements Decision {
        }

        record Skip(ReadSourceSelector.Source source) implements Decision {
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
        while (true) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("a freshness read must run outside a transaction");
            }
            ChosenReadSource.clear();
            ScopedRead<?> read;
            try {
                read = attempt.run();
            } catch (ScopedMiss miss) {
                if (decide(miss.scopes(), servedSource()) instanceof Decision.Serve) {
                    throw miss;
                }
                continue;
            }
            if (decide(read.scopes(), servedSource()) instanceof Decision.Serve) {
                return read;
            }
        }
    }

    private ReadSourceSelector.Source servedSource() {
        return ChosenReadSource.name()
                .flatMap(name -> selector.sources().stream().filter(source -> source.name().equals(name)).findFirst())
                .or(selector::current)
                .orElseThrow(ReadTemporarilyUnavailableException::new);
    }

    public Decision decide(Collection<FreshnessScope> scopes, ReadSourceSelector.Source source) {
        if (source.authoritative()) {
            return new Decision.Serve();
        }
        Collection<MarkerView> views = markers.read(scopes).values();
        if (views.stream().anyMatch(MarkerView.Unknown.class::isInstance)) {
            return skip(source);
        }
        Set<String> marked = views.stream()
                .filter(MarkerView.At.class::isInstance)
                .map(view -> ((MarkerView.At) view).gtidSet())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (marked.isEmpty()) {
            return new Decision.Serve();
        }
        String required = GtidWriteCapture.unionGtidSets(marked);
        if (isRemembered(source, required) || await(source, required)) {
            return new Decision.Serve();
        }
        return skip(source);
    }

    private boolean isRemembered(ReadSourceSelector.Source source, String required) {
        String applied = appliedBySource.get(source.name());
        return applied != null && union(applied, required).equals(applied);
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

    private static Decision skip(ReadSourceSelector.Source source) {
        ReadSourceSelector.skip(source);
        return new Decision.Skip(source);
    }

    private static String union(String left, String right) {
        return GtidWriteCapture.unionGtidSets(new LinkedHashSet<>(List.of(left, right)));
    }
}
