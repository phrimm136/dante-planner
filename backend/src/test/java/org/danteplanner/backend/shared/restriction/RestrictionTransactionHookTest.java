package org.danteplanner.backend.shared.restriction;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.user.api.UserBannedException;
import org.danteplanner.restrictionfixture.DeclaredHandlersFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RestrictionTransactionHookTest {

    private static final long PRINCIPAL = 42L;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final List<Long> checkedUsers = new ArrayList<>();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private RuntimeException refusal;

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void afterBegin_WhenARestrictedRequestBeginsAWriteTransaction_ChecksThePrincipal() {
        bindRequest("restricted", PRINCIPAL);

        hook(true).afterBegin(writeTransaction(), null);

        assertThat(checkedUsers).containsExactly(PRINCIPAL);
    }

    @Test
    void afterBegin_WhenTheRequestBeginsASecondWriteTransaction_DoesNotCheckAgain() {
        bindRequest("restricted", PRINCIPAL);
        RestrictionTransactionHook hook = hook(true);

        hook.afterBegin(writeTransaction(), null);
        hook.afterBegin(writeTransaction(), null);

        assertThat(checkedUsers).containsExactly(PRINCIPAL);
    }

    @Test
    void afterBegin_WhenTheTransactionIsReadOnly_DoesNotCheck() {
        bindRequest("restricted", PRINCIPAL);
        RestrictionTransactionHook hook = hook(true);

        hook.afterBegin(transaction(true), null);

        assertThat(checkedUsers).isEmpty();
        hook.afterBegin(writeTransaction(), null);
        assertThat(checkedUsers).containsExactly(PRINCIPAL);
    }

    @Test
    void afterBegin_WhenNoRequestIsBound_DoesNotCheck() {
        hook(true).afterBegin(writeTransaction(), null);

        assertThat(checkedUsers).isEmpty();
    }

    @Test
    void afterBegin_WhenTheHandlerIsExempt_DoesNotCheck() {
        bindRequest("exempt", PRINCIPAL);

        hook(true).afterBegin(writeTransaction(), null);

        assertThat(checkedUsers).isEmpty();
    }

    @Test
    void afterBegin_WhenTheHandlerIsRestrictedByState_LeavesTheCheckToTheService() {
        bindRequest("byState", PRINCIPAL);

        hook(true).afterBegin(writeTransaction(), null);

        assertThat(checkedUsers).isEmpty();
    }

    @Test
    void afterBegin_WhenTheRequestHasNoAuthenticatedPrincipal_DoesNotCheck() {
        bindRequest("restricted", null);

        hook(true).afterBegin(writeTransaction(), null);

        assertThat(checkedUsers).isEmpty();
    }

    @Test
    void afterBegin_WhenTheTransactionFailedToBegin_DoesNotCheck() {
        bindRequest("restricted", PRINCIPAL);

        hook(true).afterBegin(writeTransaction(), new IllegalStateException("no connection"));

        assertThat(checkedUsers).isEmpty();
    }

    @Test
    void afterBegin_WhenTheCheckRefuses_RollsTheBegunTransactionBackAndThrows() {
        bindRequest("restricted", PRINCIPAL);
        refusal = new UserBannedException(PRINCIPAL, null);
        DefaultTransactionStatus transaction = writeTransaction();

        assertThatThrownBy(() -> hook(true).afterBegin(transaction, null)).isSameAs(refusal);

        verify(transactionManager).rollback(transaction);
    }

    @Test
    void beforeCommit_WhenARestrictedByStateRequestCommitsWithoutADecision_ThrowsUnderFailFast() {
        bindRequest("byState", PRINCIPAL);

        assertThatThrownBy(() -> hook(true).beforeCommit(writeTransaction()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DeclaredHandlersFixture.byState is @RestrictedByState");
    }

    @Test
    void beforeCommit_WhenARestrictedByStateRequestCommitsWithoutADecision_CountsItWithoutFailFast() {
        bindRequest("byState", PRINCIPAL);

        hook(false).beforeCommit(writeTransaction());

        assertThat(meterRegistry.counter(RestrictionTransactionHook.UNDECIDED_COUNTER).count()).isEqualTo(1.0);
    }

    @Test
    void beforeCommit_WhenTheServiceRecordedADecision_Passes() {
        bindRequest("byState", PRINCIPAL);
        RestrictionTransactionHook hook = hook(true);

        RestrictionTransactionHook.recordDecision();

        assertThatCode(() -> hook.beforeCommit(writeTransaction())).doesNotThrowAnyException();
    }

    @Test
    void beforeCommit_WhenTheUndecidedTransactionIsReadOnly_Passes() {
        bindRequest("byState", PRINCIPAL);

        assertThatCode(() -> hook(true).beforeCommit(transaction(true))).doesNotThrowAnyException();
    }

    @Test
    void afterSingletonsInstantiated_WhenRunTwice_RegistersTheHookOnTheTransactionManagerOnce() {
        DataSourceTransactionManager manager = new DataSourceTransactionManager();
        RestrictionTransactionHook hook = hookOn(manager, true);

        hook.afterSingletonsInstantiated();
        hook.afterSingletonsInstantiated();

        assertThat(manager.getTransactionExecutionListeners()).containsExactly(hook);
    }

    private RestrictionTransactionHook hook(boolean failFast) {
        return hookOn(transactionManager, failFast);
    }

    private RestrictionTransactionHook hookOn(PlatformTransactionManager manager, boolean failFast) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        RestrictionCheckHandler handler = userId -> {
            checkedUsers.add(userId);
            if (refusal != null) {
                throw refusal;
            }
        };
        beans.addBean("restrictionCheckHandler", handler);
        beans.addBean("transactionManager", manager);
        return new RestrictionTransactionHook(beans.getBeanProvider(RestrictionCheckHandler.class),
                beans.getBeanProvider(PlatformTransactionManager.class), meterRegistry, failFast);
    }

    private static DefaultTransactionStatus writeTransaction() {
        return transaction(false);
    }

    private static DefaultTransactionStatus transaction(boolean readOnly) {
        return new DefaultTransactionStatus(null, new Object(), true, true, false, readOnly, false, null);
    }

    private static void bindRequest(String handlerMethod, Long principal) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        try {
            request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, new HandlerMethod(
                    new DeclaredHandlersFixture(), DeclaredHandlersFixture.class.getMethod(handlerMethod)));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        if (principal != null) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        }
    }
}
