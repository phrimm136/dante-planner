package org.danteplanner.backend.shared.restriction;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.shared.failure.OnUndeclared;
import org.danteplanner.backend.shared.security.Viewer;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.user.api.UserBannedException;
import org.danteplanner.fixture.restriction.DeclaredHandlersFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RestrictionTransactionHookTest {

    private static final long PRINCIPAL = 42L;
    private static final String ON_UNDECIDED = "restriction.on-undecided";

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

        hook(OnUndeclared.FAIL).afterBegin(writeTransaction(), null);

        assertThat(checkedUsers).containsExactly(PRINCIPAL);
    }

    @Test
    void afterBegin_WhenTheRequestBeginsASecondWriteTransaction_DoesNotCheckAgain() {
        bindRequest("restricted", PRINCIPAL);
        RestrictionTransactionHook hook = hook(OnUndeclared.FAIL);

        hook.afterBegin(writeTransaction(), null);
        hook.afterBegin(writeTransaction(), null);

        assertThat(checkedUsers).containsExactly(PRINCIPAL);
    }

    @Test
    void afterBegin_WhenTheTransactionIsReadOnly_DoesNotCheck() {
        bindRequest("restricted", PRINCIPAL);
        RestrictionTransactionHook hook = hook(OnUndeclared.FAIL);

        hook.afterBegin(transaction(true), null);

        assertThat(checkedUsers).isEmpty();
        hook.afterBegin(writeTransaction(), null);
        assertThat(checkedUsers).containsExactly(PRINCIPAL);
    }

    @Test
    void afterBegin_WhenNoRequestIsBound_DoesNotCheck() {
        hook(OnUndeclared.FAIL).afterBegin(writeTransaction(), null);

        assertThat(checkedUsers).isEmpty();
    }

    @Test
    void afterBegin_WhenTheHandlerIsExempt_DoesNotCheck() {
        bindRequest("exempt", PRINCIPAL);

        hook(OnUndeclared.FAIL).afterBegin(writeTransaction(), null);

        assertThat(checkedUsers).isEmpty();
    }

    @Test
    void afterBegin_WhenTheHandlerIsRestrictedByState_LeavesTheCheckToTheService() {
        bindRequest("byState", PRINCIPAL);

        hook(OnUndeclared.FAIL).afterBegin(writeTransaction(), null);

        assertThat(checkedUsers).isEmpty();
    }

    @Test
    void afterBegin_WhenTheRequestHasNoAuthenticatedPrincipal_DoesNotCheck() {
        bindRequest("restricted", null);

        hook(OnUndeclared.FAIL).afterBegin(writeTransaction(), null);

        assertThat(checkedUsers).isEmpty();
    }

    @Test
    void afterBegin_WhenTheTransactionFailedToBegin_DoesNotCheck() {
        bindRequest("restricted", PRINCIPAL);

        hook(OnUndeclared.FAIL).afterBegin(writeTransaction(), new IllegalStateException("no connection"));

        assertThat(checkedUsers).isEmpty();
    }

    @Test
    void afterBegin_WhenTheCheckRefuses_RollsTheBegunTransactionBackAndThrows() {
        bindRequest("restricted", PRINCIPAL);
        refusal = new UserBannedException(PRINCIPAL, null);
        DefaultTransactionStatus transaction = writeTransaction();

        assertThatThrownBy(() -> hook(OnUndeclared.FAIL).afterBegin(transaction, null)).isSameAs(refusal);

        verify(transactionManager).rollback(transaction);
    }

    @Test
    void afterBegin_WhenTheCheckRefusedAnEarlierWriteTransaction_ChecksTheNextOneAgain() {
        bindRequest("restricted", PRINCIPAL);
        refusal = new UserBannedException(PRINCIPAL, null);
        RestrictionTransactionHook hook = hook(OnUndeclared.FAIL);

        assertThatThrownBy(() -> hook.afterBegin(writeTransaction(), null)).isSameAs(refusal);
        assertThatThrownBy(() -> hook.afterBegin(writeTransaction(), null)).isSameAs(refusal);

        assertThat(checkedUsers).containsExactly(PRINCIPAL, PRINCIPAL);
    }

    @Test
    void afterBegin_WhenTheCheckPasses_StoresTheClearedMember() {
        bindRequest("restricted", PRINCIPAL);

        hook(OnUndeclared.FAIL).afterBegin(writeTransaction(), null);

        assertThat(storedDuty()).isEqualTo(new RestrictionDuty.Cleared(member(PRINCIPAL)));
    }

    @Test
    void record_WhenARestrictedByStateRequestRecordsAClearance_StoresIt() {
        bindRequest("byState", PRINCIPAL);

        RestrictionTransactionHook.record(new PrivateWrite());

        assertThat(storedDuty()).isEqualTo(new RestrictionDuty.Cleared(new PrivateWrite()));
    }

    @Test
    void record_WhenTheRequestIsRestricted_LeavesTheCheckDue() {
        bindRequest("restricted", PRINCIPAL);

        RestrictionTransactionHook.record(new PrivateWrite());

        assertThat(storedDuty()).isEqualTo(new RestrictionDuty.CheckDue(PRINCIPAL));
    }

    @Test
    void beforeCommit_WhenARestrictedByStateRequestCommitsWithoutADecision_ThrowsUnderFailFast() {
        bindRequest("byState", PRINCIPAL);

        assertThatThrownBy(() -> hook(OnUndeclared.FAIL).beforeCommit(writeTransaction()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DeclaredHandlersFixture.byState is @RestrictedByState");
    }

    @Test
    void beforeCommit_WhenARestrictedByStateRequestCommitsWithoutADecision_CountsItWithoutFailFast() {
        bindRequest("byState", PRINCIPAL);

        hook(OnUndeclared.COUNT).beforeCommit(writeTransaction());

        assertThat(meterRegistry.counter(RestrictionTransactionHook.UNDECIDED_COUNTER).count()).isEqualTo(1.0);
    }

    @Test
    void beforeCommit_WhenTheServiceRecordedADecision_Passes() {
        bindRequest("byState", PRINCIPAL);
        RestrictionTransactionHook hook = hook(OnUndeclared.FAIL);

        RestrictionTransactionHook.record(new PrivateWrite());

        assertThatCode(() -> hook.beforeCommit(writeTransaction())).doesNotThrowAnyException();
    }

    @Test
    void beforeCommit_WhenTheUndecidedTransactionIsReadOnly_Passes() {
        bindRequest("byState", PRINCIPAL);

        assertThatCode(() -> hook(OnUndeclared.FAIL).beforeCommit(transaction(true))).doesNotThrowAnyException();
    }

    @Test
    void beforeCommit_WhenUndecidedWithoutFailFast_WarnsWithTheHandler() {
        bindRequest("byState", PRINCIPAL);

        assertThat(LogCapture.during(() -> hook(OnUndeclared.COUNT).beforeCommit(writeTransaction()), Level.WARN,
                RestrictionTransactionHook.class))
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("DeclaredHandlersFixture.byState is @RestrictedByState but committed a write without "
                        + "calling RestrictionCheck.record");
    }

    @Test
    void hook_WhenNoRequestIsBound_DoesNothing() {
        assertThatCode(() -> {
            RestrictionTransactionHook.record(new PrivateWrite());
            hook(OnUndeclared.FAIL).afterBegin(writeTransaction(), null);
            hook(OnUndeclared.FAIL).beforeCommit(writeTransaction());
        }).doesNotThrowAnyException();

        assertThat(checkedUsers).isEmpty();
    }

    @Test
    void hook_WhenTheRequestHasNoHandler_DoesNothingAndStoresNothing() {
        ServletRequestAttributes attributes = new ServletRequestAttributes(new MockHttpServletRequest());
        RequestContextHolder.setRequestAttributes(attributes);

        assertThatCode(() -> {
            RestrictionTransactionHook.record(new PrivateWrite());
            hook(OnUndeclared.FAIL).afterBegin(writeTransaction(), null);
            hook(OnUndeclared.FAIL).beforeCommit(writeTransaction());
        }).doesNotThrowAnyException();

        assertThat(checkedUsers).isEmpty();
        assertThat(attributes.getAttributeNames(RequestAttributes.SCOPE_REQUEST)).isEmpty();
    }

    @Test
    void readOnlyTransaction_WhenARestrictedRequestRunsIt_StoresNothing() {
        bindRequest("restricted", PRINCIPAL);
        RestrictionTransactionHook hook = hook(OnUndeclared.FAIL);

        hook.afterBegin(transaction(true), null);
        hook.beforeCommit(transaction(true));

        assertThat(storedDuty()).isEqualTo(new RestrictionDuty.CheckDue(PRINCIPAL));
    }

    @Test
    void beforeCommit_WhenARestrictedRequestCommits_Passes() {
        bindRequest("restricted", PRINCIPAL);

        assertThatCode(() -> hook(OnUndeclared.FAIL).beforeCommit(writeTransaction())).doesNotThrowAnyException();
    }

    @Test
    void duty_WhenTheHandlerCarriesNeitherDeclaration_IsDischarged() {
        assertThat(RestrictionDuty.of(handler("read"), new Viewer.Member(PRINCIPAL)))
                .isEqualTo(new RestrictionDuty.Discharged());
    }

    @Test
    void duty_WhenTheHandlerIsNotAHandlerMethod_IsDischarged() {
        assertThat(RestrictionDuty.of(new ResourceHttpRequestHandler(), new Viewer.Member(PRINCIPAL)))
                .isEqualTo(new RestrictionDuty.Discharged());
    }

    @Test
    void duty_WhenAGuestReachesARestrictedHandler_IsDischarged() {
        assertThat(RestrictionDuty.of(handler("restricted"), new Viewer.Guest()))
                .isEqualTo(new RestrictionDuty.Discharged());
    }

    @Test
    void afterSingletonsInstantiated_WhenRunTwice_RegistersTheHookOnTheTransactionManagerOnce() {
        DataSourceTransactionManager manager = new DataSourceTransactionManager();
        RestrictionTransactionHook hook = hookOn(manager, OnUndeclared.FAIL);

        hook.afterSingletonsInstantiated();
        hook.afterSingletonsInstantiated();

        assertThat(manager.getTransactionExecutionListeners()).containsExactly(hook);
    }

    private ApplicationContextRunner bound(String onUndecided) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(RestrictionTransactionHook.class)
                .withBean(MeterRegistry.class, () -> meterRegistry)
                .withBean(PlatformTransactionManager.class,
                        () -> new DataSourceTransactionManager(new SimpleDriverDataSource()))
                .withPropertyValues(ON_UNDECIDED + "=" + onUndecided);
    }

    @Test
    void onUndecided_WhenThePropertyIsFail_Throws() {
        bound("fail").run(context -> {
            bindRequest("byState", PRINCIPAL);

            assertThatThrownBy(() -> context.getBean(RestrictionTransactionHook.class).beforeCommit(writeTransaction()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DeclaredHandlersFixture.byState is @RestrictedByState");
        });
    }

    @Test
    void onUndecided_WhenThePropertyIsCount_Counts() {
        bound("count").run(context -> {
            bindRequest("byState", PRINCIPAL);

            context.getBean(RestrictionTransactionHook.class).beforeCommit(writeTransaction());

            assertThat(meterRegistry.counter(RestrictionTransactionHook.UNDECIDED_COUNTER).count()).isEqualTo(1.0);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "true", "yes", "refuse"})
    void onUndecided_WhenThePropertyNamesNoPolicy_FailsStartup(String value) {
        bound(value).run(context -> assertThat(context).hasFailed());
    }

    private RestrictionTransactionHook hook(OnUndeclared onUndecided) {
        return hookOn(transactionManager, onUndecided);
    }

    private RestrictionTransactionHook hookOn(PlatformTransactionManager manager, OnUndeclared onUndecided) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        RestrictionCheckHandler handler = userId -> {
            checkedUsers.add(userId);
            if (refusal != null) {
                throw refusal;
            }
            return member(userId);
        };
        beans.addBean("restrictionCheckHandler", handler);
        beans.addBean("transactionManager", manager);
        return new RestrictionTransactionHook(beans.getBeanProvider(RestrictionCheckHandler.class),
                beans.getBeanProvider(PlatformTransactionManager.class), meterRegistry, onUndecided);
    }

    private static UnrestrictedMember member(Long userId) {
        return UnrestrictedMember.parse(userId, new Restriction.Active())
                .orElseThrow(imposed -> new IllegalStateException("an active restriction refused: " + imposed));
    }

    private static Object storedDuty() {
        return RequestContextHolder.currentRequestAttributes()
                .getAttribute(RestrictionDuty.DUTY_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    }

    private static DefaultTransactionStatus writeTransaction() {
        return transaction(false);
    }

    private static DefaultTransactionStatus transaction(boolean readOnly) {
        return new DefaultTransactionStatus(null, new Object(), true, true, false, readOnly, false, null);
    }

    private static void bindRequest(String handlerMethod, Long principal) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        if (principal != null) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        }
        new RestrictionDutyInterceptor().preHandle(request, new MockHttpServletResponse(), handler(handlerMethod));
    }

    private static HandlerMethod handler(String handlerMethod) {
        try {
            return new HandlerMethod(new DeclaredHandlersFixture(), DeclaredHandlersFixture.class.getMethod(handlerMethod));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
