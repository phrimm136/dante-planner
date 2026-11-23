package org.danteplanner.fixture.decisionpurity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import org.danteplanner.backend.shared.failure.CommandOutcome;

final class DecisionPurityFixtures {

    private DecisionPurityFixtures() {
    }

    sealed interface Choice permits Choice.Left, Choice.Right {

        record Left() implements Choice {
        }

        record Right() implements Choice {
        }
    }

    static final class Decider {

        Choice decide() throws InterruptedException {
            new CountDownLatch(1).await();
            return new Choice.Left();
        }
    }

    interface Port {
        boolean isOpen();
    }

    static final class PortReader {

        private final Port port;

        PortReader(Port port) {
            this.port = port;
        }

        Choice decide() {
            return switch (Boolean.valueOf(port.isOpen())) {
                case Boolean open when open -> new Choice.Left();
                default -> new Choice.Right();
            };
        }
    }

    sealed interface Window permits Window.Closed, Window.Open {

        record Closed() implements Window {
        }

        record Open(String scope) implements Window {
        }
    }

    static final class WindowHolder {

        private static final ThreadLocal<Window> HOLDER = ThreadLocal.withInitial(Window.Closed::new);

        static Window window() {
            return HOLDER.get();
        }

        static Choice fromWindow() {
            return switch (window()) {
                case Window.Closed closed -> new Choice.Left();
                case Window.Open open -> new Choice.Right();
            };
        }

        static Window windowOrClosed() {
            Window held = HOLDER.get();
            return held == null ? new Window.Closed() : held;
        }
    }

    enum WaitOutcome implements CommandOutcome {
        WAITED
    }

    static final class Waiter {

        WaitOutcome run() throws InterruptedException {
            new CountDownLatch(1).await();
            return WaitOutcome.WAITED;
        }
    }

    enum BarOutcome {
        DONE
    }

    record Baz() implements CommandOutcome {
    }

    static final class Pricing {

        Choice choose(int amount) {
            return amount > 0 ? new Choice.Left() : new Choice.Right();
        }
    }

    @Component
    static final class Ledger {

        private final Pricing pricing;
        private final ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();

        Ledger(Pricing pricing) {
            this.pricing = pricing;
        }

        Choice priced() {
            return pricing.choose(1);
        }

        Choice counted() {
            return counts.isEmpty() ? new Choice.Left() : new Choice.Right();
        }
    }

    @FunctionalInterface
    private interface Rule {
        Choice apply(int amount);
    }

    static final class RuleRunner {

        private final Rule rule = amount -> new Choice.Left();

        Choice decide() {
            return rule.apply(1);
        }
    }

    static final class PublicPortReader {

        private final PublicReads reads;

        PublicPortReader(PublicReads reads) {
            this.reads = reads;
        }

        Choice decide() {
            return reads.read() ? new Choice.Left() : new Choice.Right();
        }
    }

    static final class Comparer {

        static Choice same(Window left, Window right) {
            return left.equals(right) ? new Choice.Left() : new Choice.Right();
        }
    }

    static final class ClockReader {

        private final Clock clock;

        ClockReader(Clock clock) {
            this.clock = clock;
        }

        Choice decide() {
            return clock.instant().isAfter(Instant.EPOCH) ? new Choice.Left() : new Choice.Right();
        }
    }

    static final class FutureReader {

        static Choice decide(Future<Boolean> answer) throws InterruptedException, ExecutionException {
            return answer.get() ? new Choice.Left() : new Choice.Right();
        }
    }

    static final class Minter {

        static Choice decide() {
            return UUID.randomUUID().toString().isEmpty() ? new Choice.Left() : new Choice.Right();
        }
    }

    static final class PureValues {

        static Choice decide(long millis) {
            return List.of(Duration.ofMillis(millis)).isEmpty() ? new Choice.Left() : new Choice.Right();
        }
    }

    static final class Cache {

        private final ConcurrentHashMap<String, Integer> entries = new ConcurrentHashMap<>();

        Choice decide() {
            return entries.isEmpty() ? new Choice.Left() : new Choice.Right();
        }
    }

    static final class CacheConfig {

        @Bean
        Cache cache() {
            return new Cache();
        }
    }

    static final class Tally {

        private static int count;

        static void record() {
            count++;
        }

        static Choice decide() {
            return count > 0 ? new Choice.Left() : new Choice.Right();
        }
    }

    enum Status {
        DRAFT,
        LIVE
    }

    static final class Row {

        private Status status = Status.DRAFT;

        void publish() {
            status = Status.LIVE;
        }

        Status getStatus() {
            return status;
        }
    }

    static final class Ranks {

        private static final Map<String, Integer> BY_NAME = Map.of("left", 1);

        static Choice decide(String name) {
            return BY_NAME.containsKey(name) ? new Choice.Left() : new Choice.Right();
        }
    }

    static final class Seen {

        private static final Map<String, Integer> BY_NAME = new HashMap<>();

        static void remember(String name) {
            BY_NAME.put(name, 1);
        }

        static Choice decide(String name) {
            return BY_NAME.containsKey(name) ? new Choice.Left() : new Choice.Right();
        }
    }

    static final class Slots {

        private static final int[] USED = new int[1];

        static void use() {
            USED[0] = 1;
        }

        static Choice decide() {
            return USED[0] > 0 ? new Choice.Left() : new Choice.Right();
        }
    }

    static final class Vocabulary {

        private static final Set<String> NAMES = Set.of("left");

        static Choice decide(Set<String> given) {
            Set<String> kept = new HashSet<>(given);
            kept.retainAll(NAMES);
            return kept.isEmpty() ? new Choice.Left() : new Choice.Right();
        }
    }

    static final class Pool {

        private static final Set<String> NAMES = new HashSet<>();

        static Choice decide(Set<String> given) {
            Set<String> kept = new HashSet<>(given);
            NAMES.retainAll(kept);
            return kept.isEmpty() ? new Choice.Left() : new Choice.Right();
        }
    }

    static final class Broken {

        private static final Set<String> NAMES = Set.of(unavailable());

        private static String unavailable() {
            throw new IllegalStateException("no value");
        }

        static Choice decide(Set<String> given) {
            Set<String> kept = new HashSet<>(given);
            kept.retainAll(NAMES);
            return kept.isEmpty() ? new Choice.Left() : new Choice.Right();
        }
    }

    static final class MonitorWaiter {

        private final Object monitor = new Object();

        Choice decide() throws InterruptedException {
            synchronized (monitor) {
                monitor.wait(1);
            }
            return new Choice.Left();
        }
    }

    static class Base {

        boolean isLeft() {
            return true;
        }
    }

    static final class Overriding extends Base {

        private final Port port;

        Overriding(Port port) {
            this.port = port;
        }

        @Override
        boolean isLeft() {
            return port.isOpen();
        }
    }

    static final class BaseCaller {

        Choice decide(Base base) {
            return base.isLeft() ? new Choice.Left() : new Choice.Right();
        }
    }

    interface Asker {
        boolean ask();
    }

    static final class PureAsker implements Asker {

        @Override
        public boolean ask() {
            return true;
        }
    }

    static class Asking {

        private final Port port;

        Asking(Port port) {
            this.port = port;
        }

        public boolean ask() {
            return port.isOpen();
        }
    }

    static final class InheritedAsker extends Asking implements Asker {

        InheritedAsker(Port port) {
            super(port);
        }
    }

    static final class AskerCaller {

        Choice decide(Asker asker) {
            return asker.ask() ? new Choice.Left() : new Choice.Right();
        }
    }
}
