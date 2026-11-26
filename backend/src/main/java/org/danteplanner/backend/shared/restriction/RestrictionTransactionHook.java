package org.danteplanner.backend.shared.restriction;

import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ConfigurableTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.method.HandlerMethod;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.failure.OnUndeclared;
import org.danteplanner.backend.shared.security.SpringContextAdapter;
import org.jspecify.annotations.Nullable;

@Component
@Slf4j
public class RestrictionTransactionHook implements TransactionExecutionListener, SmartInitializingSingleton {

    static final String UNDECIDED_COUNTER = "restriction.undecided";

    private final ObjectProvider<RestrictionCheckHandler> checkHandler;
    private final ObjectProvider<PlatformTransactionManager> transactionManager;
    private final Counter undecidedCounter;
    private final OnUndeclared onUndecided;

    public RestrictionTransactionHook(
            ObjectProvider<RestrictionCheckHandler> checkHandler,
            ObjectProvider<PlatformTransactionManager> transactionManager,
            MeterRegistry meterRegistry,
            @Value("${restriction.on-undecided}") OnUndeclared onUndecided) {
        this.checkHandler = checkHandler;
        this.transactionManager = transactionManager;
        this.undecidedCounter = Counter.builder(UNDECIDED_COUNTER).register(meterRegistry);
        this.onUndecided = Objects.requireNonNull(onUndecided, "restriction.on-undecided");
    }

    public static void recordDecision() {
        switch (duty()) {
            case RestrictionDuty.DecisionDue due -> SpringContextAdapter.storeRequestValue(
                    RestrictionDuty.DUTY_ATTRIBUTE, new RestrictionDuty.Discharged());
            case RestrictionDuty.CheckDue due -> { }
            case RestrictionDuty.Discharged discharged -> { }
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        PlatformTransactionManager manager = transactionManager.getObject();
        if (!(manager instanceof ConfigurableTransactionManager configurable)) {
            throw new IllegalStateException("Restriction checks need a transaction manager that accepts execution "
                    + "listeners, but " + manager.getClass().getName() + " does not");
        }
        if (!configurable.getTransactionExecutionListeners().contains(this)) {
            configurable.addListener(this);
        }
    }

    @Override
    public void afterBegin(TransactionExecution transaction, @Nullable Throwable beginFailure) {
        switch (BeginDecision.of(beginFailure, transaction.isReadOnly(), duty())) {
            case BeginDecision.CheckUser check -> check(check.userId(), transaction);
            case BeginDecision.Skip skip -> { }
        }
    }

    private void check(Long userId, TransactionExecution transaction) {
        SpringContextAdapter.storeRequestValue(RestrictionDuty.DUTY_ATTRIBUTE, new RestrictionDuty.Discharged());
        try {
            checkHandler.getObject().requireUnrestricted(userId);
        } catch (RuntimeException refusal) {
            // spring-tx does not catch an exception thrown from afterBegin, so the begun transaction would stay bound.
            transactionManager.getObject().rollback((TransactionStatus) transaction);
            throw refusal;
        }
    }

    @Override
    public void beforeCommit(TransactionExecution transaction) {
        switch (CommitDecision.of(transaction.isReadOnly(), duty())) {
            case CommitDecision.Report report -> reportUndecided(report.handler());
            case CommitDecision.Pass pass -> { }
        }
    }

    private void reportUndecided(HandlerMethod handler) {
        switch (onUndecided) {
            case FAIL -> throw new IllegalStateException(undecidedMessage(handler));
            case COUNT -> {
                undecidedCounter.increment();
                log.warn(undecidedMessage(handler));
            }
        }
    }

    private static String undecidedMessage(HandlerMethod handler) {
        return handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName()
                + " is @RestrictedByState but committed a write without calling "
                + "RestrictionCheck.requireUnrestricted or RestrictionCheck.recordPrivate";
    }

    private static RestrictionDuty duty() {
        return SpringContextAdapter.requestValue(RestrictionDuty.DUTY_ATTRIBUTE, RestrictionDuty.class,
                new RestrictionDuty.Discharged());
    }

    private sealed interface BeginDecision {

        record CheckUser(Long userId) implements BeginDecision {
        }

        record Skip() implements BeginDecision {
        }

        private static BeginDecision of(@Nullable Throwable beginFailure, boolean readOnly, RestrictionDuty duty) {
            return beginFailure == null && !readOnly && duty instanceof RestrictionDuty.CheckDue due
                    ? new CheckUser(due.userId())
                    : new Skip();
        }
    }

    private sealed interface CommitDecision {

        record Report(HandlerMethod handler) implements CommitDecision {
        }

        record Pass() implements CommitDecision {
        }

        private static CommitDecision of(boolean readOnly, RestrictionDuty duty) {
            return !readOnly && duty instanceof RestrictionDuty.DecisionDue due
                    ? new Report(due.handler())
                    : new Pass();
        }
    }
}
