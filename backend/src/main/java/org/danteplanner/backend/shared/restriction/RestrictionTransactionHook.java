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
        RequestState state = currentState();
        if (state != null) {
            state.decided = true;
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
        if (beginFailure != null || transaction.isReadOnly()) {
            return;
        }
        RequestState state = currentState();
        if (state == null || !state.checkPending()) {
            return;
        }
        switch (state.principal) {
            case Principal.Authenticated authenticated -> check(state, authenticated.userId(), transaction);
            case Principal.Anonymous anonymous -> { }
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
        if (transaction.isReadOnly()) {
            return;
        }
        RequestState state = currentState();
        if (state == null) {
            return;
        }
        if (state.declaration == Declaration.BY_STATE && !state.decided) {
            reportUndecided(state);
        }
    }

    private void reportUndecided(RequestState state) {
        String message = state.handlerName + " is @RestrictedByState but committed a write without calling "
                + "RestrictionCheck.requireUnrestricted or RestrictionCheck.recordPrivate";
        if (undecidedFailFast) {
            throw new IllegalStateException(message);
        }
        undecidedCounter.increment();
        log.warn(message);
    }

    @Nullable
    private static RequestState currentState() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        if (attributes.getAttribute(STATE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof RequestState state) {
            return state;
        }
        if (!(attributes.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST) instanceof HandlerMethod handler)) {
            return null;
        }
        Principal principal = switch (SignIn.of(SecurityContextHolder.getContext().getAuthentication())) {
            case SignIn.Present present -> Principal.of(present.authentication().getPrincipal());
            case SignIn.Absent absent -> new Principal.Anonymous();
        };
        RequestState state = new RequestState(declarationOf(handler),
                handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName(), principal);
        attributes.setAttribute(STATE_ATTRIBUTE, state, RequestAttributes.SCOPE_REQUEST);
        return state;
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

        private boolean checkPending() {
            return declaration == Declaration.RESTRICTED && !checked;
        }
    }
}
