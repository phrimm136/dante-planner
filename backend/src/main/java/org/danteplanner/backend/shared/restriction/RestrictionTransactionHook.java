package org.danteplanner.backend.shared.restriction;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
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
        state.checked = true;
        try {
            checkHandler.getObject().requireUnrestricted(state.userId);
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
        RequestState state = new RequestState(declarationOf(handler),
                handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName(), principalId());
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

    @Nullable
    private static Long principalId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    private enum Declaration {
        RESTRICTED,
        BY_STATE,
        NONE
    }

    private static final class RequestState {

        private final Declaration declaration;
        private final String handlerName;
        @Nullable
        private final Long userId;
        private boolean checked;
        private boolean decided;

        private RequestState(Declaration declaration, String handlerName, @Nullable Long userId) {
            this.declaration = declaration;
            this.handlerName = handlerName;
            this.userId = userId;
        }

        private boolean checkPending() {
            return declaration == Declaration.RESTRICTED && !checked && userId != null;
        }
    }
}
