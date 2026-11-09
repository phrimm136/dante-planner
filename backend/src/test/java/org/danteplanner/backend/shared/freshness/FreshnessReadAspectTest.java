package org.danteplanner.backend.shared.freshness;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.freshness.MarkerStore.MarkerView;
import org.danteplanner.backend.shared.gtid.GtidGateConfig;
import org.danteplanner.backend.shared.readpath.ChosenReadSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FreshnessReadAspectTest {

    private static final String SEOUL = "seoul-replica";
    private static final String APPLIED_FIVE = "3e11fa47-71ca-11e1-9e33-c80aa9429562:1-5";
    private static final FreshnessScope ENTITY = new FreshnessScope.Entity(UUID.randomUUID());

    @Mock
    private DataSource seoulPool;

    @Mock
    private DataSource primaryPool;

    @Mock
    private Connection seoulConnection;

    @Mock
    private Connection primaryConnection;

    private final List<Collection<FreshnessScope>> markerReads = new ArrayList<>();
    private final List<Boolean> transactionActiveAtMarkerRead = new ArrayList<>();
    private final Map<FreshnessScope, MarkerView> views = new LinkedHashMap<>();
    private boolean waitApplies;
    private ReadSourceSelector.Source seoul;
    private ReadSourceSelector selector;
    private AnnotationConfigApplicationContext context;
    private Reads reads;

    @BeforeEach
    void setUp() throws SQLException {
        ReadSourceSelector.clearSkips();
        ChosenReadSource.clear();
        lenient().when(seoulPool.getConnection()).thenReturn(seoulConnection);
        lenient().when(primaryPool.getConnection()).thenReturn(primaryConnection);
        seoul = new ReadSourceSelector.Source(SEOUL, false, seoulPool, breaker(SEOUL));
        ReadSourceSelector.Source primary = new ReadSourceSelector.Source(
                ReadSourceProperties.PRIMARY, true, primaryPool, breaker(ReadSourceProperties.PRIMARY));
        selector = new ReadSourceSelector(List.of(seoul, primary));
        FreshnessReadGuard guard = new FreshnessReadGuard(scopes -> {
            markerReads.add(List.copyOf(scopes));
            transactionActiveAtMarkerRead.add(TransactionSynchronizationManager.isActualTransactionActive());
            return views;
        }, (source, gtidSet, bound) -> waitApplies, new FreshnessReadSettings(Duration.ofMillis(250)),
                new SimpleMeterRegistry(), selector);
        context = new AnnotationConfigApplicationContext();
        context.register(ProxyConfig.class);
        context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(selector));
        context.registerBean(FreshnessReadAspect.class, () -> new FreshnessReadAspect(guard));
        context.registerBean(Reads.class);
        context.refresh();
        reads = context.getBean(Reads.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
        RequestContextHolder.resetRequestAttributes();
        ReadSourceSelector.clearSkips();
        ChosenReadSource.clear();
    }

    private static CircuitBreaker breaker(String name) {
        return CircuitBreaker.of(name, new ReadSourceProperties.Breaker().circuitBreakerConfig());
    }

    @Configuration
    @EnableTransactionManagement
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class ProxyConfig {
    }

    record Attempt(String source, boolean transactionActive, boolean readOnly) {
    }

    static final class MissFixture extends ScopedMiss {

        MissFixture(Set<FreshnessScope> scopes) {
            super("PLANNER_NOT_FOUND", "Planner not found", scopes);
        }
    }

    static class Reads {

        private final List<Attempt> attempts = new ArrayList<>();

        public List<Attempt> attempts() {
            return attempts;
        }

        private Attempt attempt() {
            Attempt attempt = new Attempt(ChosenReadSource.name().orElse(""),
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            attempts.add(attempt);
            return attempt;
        }

        @Transactional(readOnly = true)
        public ScopedRead<String> scoped() {
            return new ScopedRead<>(attempt().source(), Set.of(ENTITY));
        }

        @Transactional(readOnly = true)
        public ScopedRead<String> missing() {
            attempt();
            throw new MissFixture(Set.of(ENTITY));
        }

        @Transactional(readOnly = true)
        public ScopedRead<String> failing() {
            attempt();
            throw new IllegalArgumentException("not the owner");
        }

        @Transactional(readOnly = true)
        @FreshnessExempt(reason = "runs with no request bound")
        public String exempt() {
            return attempt().source();
        }

        @Transactional(readOnly = true)
        @FreshnessPending(reason = "its scope arrives in M2")
        public String pending() {
            return attempt().source();
        }

        @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
        public String inner() {
            return attempt().source();
        }
    }

    private List<String> attemptSources() {
        return reads.attempts().stream().map(Attempt::source).toList();
    }

    private void unappliedMarker() {
        views.put(ENTITY, new MarkerView.At(APPLIED_FIVE));
        waitApplies = false;
    }

    @Test
    void scoped_WhenCalledWithNoTransaction_DecidesOutsideTheTransactionAndLoadsInsideAReadOnlyOne() {
        views.put(ENTITY, new MarkerView.Absent());

        ScopedRead<String> served = reads.scoped();

        assertThat(served.value()).isEqualTo(SEOUL);
        assertThat(reads.attempts()).containsExactly(new Attempt(SEOUL, true, true));
        assertThat(markerReads).containsExactly(List.of(ENTITY));
        assertThat(transactionActiveAtMarkerRead).containsExactly(false);
    }

    @Test
    void scoped_WhenTheReplicaMarkerIsNotAppliedWithinTheBound_LoadsAgainOnTheAuthority() {
        unappliedMarker();

        ScopedRead<String> served = reads.scoped();

        assertThat(served.value()).isEqualTo(ReadSourceProperties.PRIMARY);
        assertThat(reads.attempts()).containsExactly(
                new Attempt(SEOUL, true, true), new Attempt(ReadSourceProperties.PRIMARY, true, true));
        assertThat(markerReads).containsExactly(List.of(ENTITY));
    }

    @Test
    void scoped_WhenCalledInsideATransaction_LoadsOnceAndReadsNoMarker() {
        unappliedMarker();

        String served = new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                .execute(status -> reads.scoped().value());

        assertThat(served).isEqualTo(SEOUL);
        assertThat(attemptSources()).containsExactly(SEOUL);
        assertThat(markerReads).isEmpty();
    }

    @Test
    void exempt_WhenServedByTheReplica_ReadsNoMarker() {
        unappliedMarker();

        assertThat(reads.exempt()).isEqualTo(SEOUL);
        assertThat(attemptSources()).containsExactly(SEOUL);
        assertThat(markerReads).isEmpty();
    }

    @Test
    void pending_WhenServedByTheReplica_ReadsNoMarker() {
        unappliedMarker();

        assertThat(reads.pending()).isEqualTo(SEOUL);
        assertThat(attemptSources()).containsExactly(SEOUL);
        assertThat(markerReads).isEmpty();
    }

    @Test
    void inner_WhenCalledWithNoTransaction_FailsBeforeLoading() throws SQLException {
        assertThatThrownBy(reads::inner).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(reads.attempts()).isEmpty();
        verify(seoulPool, never()).getConnection();
        verify(primaryPool, never()).getConnection();
    }

    @Test
    void scoped_WhenNoRequestIsBound_ClearsTheSkipsItAdded() {
        unappliedMarker();

        reads.scoped();

        assertThat(attemptSources()).containsExactly(SEOUL, ReadSourceProperties.PRIMARY);
        assertThat(selector.current()).contains(seoul);
    }

    @Test
    void scoped_WhenARequestIsBound_KeepsItsSkipsForTheRestOfTheRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        unappliedMarker();

        reads.scoped();

        assertThat(attemptSources()).containsExactly(SEOUL, ReadSourceProperties.PRIMARY);
        assertThat(selector.current()).map(ReadSourceSelector.Source::name).contains(ReadSourceProperties.PRIMARY);
    }

    @Test
    void missing_WhenTheMissHasNoMarker_ThrowsAfterOneAttemptWithoutTouchingTheAuthority() throws SQLException {
        views.put(ENTITY, new MarkerView.Absent());

        assertThatThrownBy(reads::missing).isInstanceOf(MissFixture.class);
        assertThat(attemptSources()).containsExactly(SEOUL);
        assertThat(markerReads).containsExactly(List.of(ENTITY));
        verify(primaryPool, never()).getConnection();
    }

    @Test
    void failing_WhenTheLoadThrowsAnythingButAScopedMiss_RethrowsWithoutReadingAMarker() {
        unappliedMarker();

        assertThatThrownBy(reads::failing).isInstanceOf(IllegalArgumentException.class).hasMessage("not the owner");
        assertThat(attemptSources()).containsExactly(SEOUL);
        assertThat(markerReads).isEmpty();
    }

    @Test
    void gtidGateConfig_WhenRoutingIsDisabled_RegistersNoFreshnessReadAspect() {
        new ApplicationContextRunner()
                .withUserConfiguration(GtidGateConfig.class)
                .withPropertyValues("datasource.routing.enabled=false")
                .run(disabled -> assertThat(disabled).doesNotHaveBean(FreshnessReadAspect.class));
    }
}
