package org.danteplanner.backend.shared.restriction;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ConfigurableTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.failure.CommandOutcome;
import org.jspecify.annotations.Nullable;

@Component
@Slf4j
public class RestrictionTransactionHook implements TransactionExecutionListener, SmartInitializingSingleton {

    static final String UNDECIDED_COUNTER = "restriction.undecided";
    private static final String STATE_ATTRIBUTE = RestrictionTransactionHook.class.getName() + ".state";

    private final ObjectProvider<RestrictionCheckHandler> checkHandler;
    private final ObjectProvider<PlatformTransactionManager> transactionManager;
    private final Counter undecidedCounter;
    private final boolean undecidedFailFast;

    public RestrictionTransactionHook(
            ObjectProvider<RestrictionCheckHandler> checkHandler,
            ObjectProvider<PlatformTransactionManager> transactionManager,
            MeterRegistry meterRegistry,
            @Value("${restriction.undecided-fail-fast}") boolean undecidedFailFast) {
        this.checkHandler = checkHandler;
        this.transactionManager = transactionManager;
        this.undecidedCounter = Counter.builder(UNDECIDED_COUNTER).register(meterRegistry);
        this.undecidedFailFast = undecidedFailFast;
    }

    public static void recordDecision() {
        switch (currentState()) {
            case RequestStateOutcome.None none -> { }
            case RequestStateOutcome.Tracked tracked -> tracked.state().decided = true;
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
        switch (Guard.begun(beginFailure, transaction.isReadOnly())) {
            case UNGUARDED -> { }
            case GUARDED -> beginGuarded(transaction);
        }
    }

    private void beginGuarded(TransactionExecution transaction) {
        switch (currentState()) {
            case RequestStateOutcome.None none -> { }
            case RequestStateOutcome.Tracked tracked -> {
                RequestState state = tracked.state();
                switch (BeginStep.of(state.declaration, state.checked, state.principal)) {
                    case BeginStep.Check check -> check(state, check.userId(), transaction);
                    case BeginStep.Pass pass -> { }
                }
            }
        }
    }

    private void check(RequestState state, Long userId, TransactionExecution transaction) {
        state.checked = true;
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
        switch (Guard.committing(transaction.isReadOnly())) {
            case UNGUARDED -> { }
            case GUARDED -> commitGuarded();
        }
    }

    private void commitGuarded() {
        switch (currentState()) {
            case RequestStateOutcome.None none -> { }
            case RequestStateOutcome.Tracked tracked -> {
                RequestState state = tracked.state();
                switch (CommitStep.of(state.declaration, state.decided, undecidedFailFast)) {
                    case PASS -> { }
                    case FAIL -> throw new IllegalStateException(undecidedMessage(state));
                    case COUNT -> {
                        undecidedCounter.increment();
                        log.warn(undecidedMessage(state));
                    }
                }
            }
        }
    }

    private static String undecidedMessage(RequestState state) {
        return state.handlerName + " is @RestrictedByState but committed a write without calling "
                + "RestrictionCheck.requireUnrestricted or RestrictionCheck.recordPrivate";
    }

    private static RequestStateOutcome currentState() {
        return switch (RequestScope.of(RequestContextHolder.getRequestAttributes())) {
            case RequestScope.Unbound unbound -> new RequestStateOutcome.None();
            case RequestScope.Bound bound -> stateIn(bound.attributes());
        };
    }

    private static RequestStateOutcome stateIn(RequestAttributes attributes) {
        return switch (Dispatch.of(
                attributes.getAttribute(STATE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST),
                attributes.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                        RequestAttributes.SCOPE_REQUEST))) {
            case Dispatch.Tracked tracked -> new RequestStateOutcome.Tracked(tracked.state());
            case Dispatch.Handled handled -> track(handled.handler(), attributes);
            case Dispatch.Unhandled unhandled -> new RequestStateOutcome.None();
        };
    }

    private static RequestStateOutcome track(HandlerMethod handler, RequestAttributes attributes) {
        Principal principal = switch (SignIn.of(SecurityContextHolder.getContext().getAuthentication())) {
            case SignIn.Present present -> Principal.of(present.authentication().getPrincipal());
            case SignIn.Absent absent -> new Principal.Anonymous();
        };
        RequestState state = new RequestState(declarationOf(handler),
                handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName(), principal);
        attributes.setAttribute(STATE_ATTRIBUTE, state, RequestAttributes.SCOPE_REQUEST);
        return new RequestStateOutcome.Tracked(state);
    }

    private static Declaration declarationOf(HandlerMethod handler) {
        if (handler.hasMethodAnnotation(Restricted.class)) {
            return Declaration.RESTRICTED;
        }
        if (handler.hasMethodAnnotation(RestrictedByState.class)) {
            return Declaration.BY_STATE;
        }
        return Declaration.NONE;
    }

    private sealed interface RequestStateOutcome extends CommandOutcome {

        record None() implements RequestStateOutcome {
        }

        record Tracked(RequestState state) implements RequestStateOutcome {
        }
    }

    private sealed interface RequestScope {

        record Bound(RequestAttributes attributes) implements RequestScope {
        }

        record Unbound() implements RequestScope {
        }

        static RequestScope of(@Nullable RequestAttributes attributes) {
            return attributes == null ? new Unbound() : new Bound(attributes);
        }
    }

    private sealed interface Dispatch {

        record Tracked(RequestState state) implements Dispatch {
        }

        record Handled(HandlerMethod handler) implements Dispatch {
        }

        record Unhandled() implements Dispatch {
        }

        static Dispatch of(@Nullable Object stored, @Nullable Object bestMatchingHandler) {
            if (stored instanceof RequestState state) {
                return new Tracked(state);
            }
            return bestMatchingHandler instanceof HandlerMethod handler ? new Handled(handler) : new Unhandled();
        }
    }

    private enum Guard {
        GUARDED,
        UNGUARDED;

        static Guard begun(@Nullable Throwable beginFailure, boolean readOnly) {
            return beginFailure == null ? committing(readOnly) : UNGUARDED;
        }

        static Guard committing(boolean readOnly) {
            return readOnly ? UNGUARDED : GUARDED;
        }
    }

    private sealed interface BeginStep {

        record Check(Long userId) implements BeginStep {
        }

        record Pass() implements BeginStep {
        }

        private static BeginStep of(Declaration declaration, boolean checked, Principal principal) {
            if (declaration != Declaration.RESTRICTED || checked) {
                return new Pass();
            }
            return switch (principal) {
                case Principal.Authenticated authenticated -> new Check(authenticated.userId());
                case Principal.Anonymous anonymous -> new Pass();
            };
        }
    }

    private enum CommitStep {
        PASS,
        FAIL,
        COUNT;

        static CommitStep of(Declaration declaration, boolean decided, boolean failFast) {
            if (declaration != Declaration.BY_STATE || decided) {
                return PASS;
            }
            return failFast ? FAIL : COUNT;
        }
    }

    private sealed interface SignIn {

        record Present(Authentication authentication) implements SignIn {
        }

        record Absent() implements SignIn {
        }

        static SignIn of(@Nullable Authentication authentication) {
            return authentication == null ? new Absent() : new Present(authentication);
        }
    }

    private sealed interface Principal {

        record Authenticated(Long userId) implements Principal {
        }

        record Anonymous() implements Principal {
        }

        static Principal of(@Nullable Object principal) {
            return principal instanceof Long userId ? new Authenticated(userId) : new Anonymous();
        }
    }

    private enum Declaration {
        RESTRICTED,
        BY_STATE,
        NONE
    }

    private static final class RequestState {

        private final Declaration declaration;
        private final String handlerName;
        private final Principal principal;
        private boolean checked;
        private boolean decided;

        private RequestState(Declaration declaration, String handlerName, Principal principal) {
            this.declaration = declaration;
            this.handlerName = handlerName;
            this.principal = principal;
        }
    }
}
