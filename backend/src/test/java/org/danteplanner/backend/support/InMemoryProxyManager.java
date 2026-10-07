package org.danteplanner.backend.support;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import io.github.bucket4j.BlockingBucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.BucketListener;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.SchedulingBucket;
import io.github.bucket4j.TokensInheritanceStrategy;
import io.github.bucket4j.VerboseBucket;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.OptimizationController;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RecoveryStrategy;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import io.github.bucket4j.distributed.proxy.optimization.Optimization;

public final class InMemoryProxyManager implements ProxyManager<byte[]> {

    public record Charge(String key, BucketConfiguration configuration) {
    }

    private final RemoteBucketBuilder<byte[]> builder = new Builder();
    private final Queue<Charge> charges = new ConcurrentLinkedQueue<>();
    private final Queue<Supplier<ConsumptionProbe>> scripted = new ConcurrentLinkedQueue<>();

    public void denyNext(long nanosToWaitForRefill) {
        scripted.add(() -> ConsumptionProbe.rejected(0, nanosToWaitForRefill, nanosToWaitForRefill));
    }

    public void failNext(RuntimeException failure) {
        scripted.add(() -> {
            throw failure;
        });
    }

    public List<Charge> charges() {
        return List.copyOf(charges);
    }

    @Override
    public RemoteBucketBuilder<byte[]> builder() {
        return builder;
    }

    @Override
    public Optional<BucketConfiguration> getProxyConfiguration(byte[] key) {
        return Optional.empty();
    }

    @Override
    public void removeProxy(byte[] key) {
    }

    @Override
    public boolean isAsyncModeSupported() {
        return false;
    }

    @Override
    public AsyncProxyManager<byte[]> asAsync() {
        throw new UnsupportedOperationException();
    }

    private ConsumptionProbe consume(byte[] key, Supplier<BucketConfiguration> configuration) {
        charges.add(new Charge(new String(key, StandardCharsets.UTF_8), configuration.get()));
        Supplier<ConsumptionProbe> outcome = scripted.poll();
        return outcome == null ? ConsumptionProbe.consumed(1, 0) : outcome.get();
    }

    private final class Builder implements RemoteBucketBuilder<byte[]> {

        @Override
        public RemoteBucketBuilder<byte[]> withRecoveryStrategy(RecoveryStrategy recoveryStrategy) {
            return this;
        }

        @Override
        public RemoteBucketBuilder<byte[]> withOptimization(Optimization optimization) {
            return this;
        }

        @Override
        public RemoteBucketBuilder<byte[]> withImplicitConfigurationReplacement(long desiredConfigurationVersion,
                TokensInheritanceStrategy tokensInheritanceStrategy) {
            return this;
        }

        @Override
        public BucketProxy build(byte[] key, Supplier<BucketConfiguration> configurationSupplier) {
            return new Proxy(key, configurationSupplier);
        }

        @Override
        public BucketProxy build(byte[] key, BucketConfiguration configuration) {
            return new Proxy(key, () -> configuration);
        }
    }

    private final class Proxy implements BucketProxy {

        private final byte[] key;
        private final Supplier<BucketConfiguration> configuration;

        private Proxy(byte[] key, Supplier<BucketConfiguration> configuration) {
            this.key = key.clone();
            this.configuration = configuration;
        }

        @Override
        public ConsumptionProbe tryConsumeAndReturnRemaining(long numTokens) {
            return consume(key, configuration);
        }

        @Override
        public BucketProxy toListenable(BucketListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OptimizationController getOptimizationController() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BlockingBucket asBlocking() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SchedulingBucket asScheduler() {
            throw new UnsupportedOperationException();
        }

        @Override
        public VerboseBucket asVerbose() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean tryConsume(long numTokens) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long consumeIgnoringRateLimits(long tokens) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EstimationProbe estimateAbilityToConsume(long numTokens) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long tryConsumeAsMuchAsPossible() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long tryConsumeAsMuchAsPossible(long limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addTokens(long tokensToAdd) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void forceAddTokens(long tokensToAdd) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reset() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getAvailableTokens() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void replaceConfiguration(BucketConfiguration newConfiguration,
                TokensInheritanceStrategy tokensInheritanceStrategy) {
            throw new UnsupportedOperationException();
        }
    }
}
