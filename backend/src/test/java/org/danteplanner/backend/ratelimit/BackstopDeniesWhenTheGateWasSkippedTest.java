package org.danteplanner.backend.ratelimit;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitInterceptor;
import org.danteplanner.backend.shared.service.RateLimitPolicy;
import org.danteplanner.backend.shared.service.RateLimitService;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.ratelimitfixture.BareHandlerFixture;
import org.danteplanner.ratelimitfixture.DeclaredHandlerFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenario: Backstop denies when the gate was skipped.
 *
 * <p>The coverage rule makes an undeclared handler unreachable in a build that ran its tests. This
 * covers the deploy where it did not: the interceptor refuses rather than waving the request
 * through, because a silently unguarded endpoint is the failure the whole seam exists to end.</p>
 */
class BackstopDeniesWhenTheGateWasSkippedTest {

    private final BareHandlerFixture bareFixture = new BareHandlerFixture();

    private RateLimitService rateLimitService;
    private Logger interceptorLogger;
    private ListAppender<ILoggingEvent> logAppender;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(RateLimitService.class);
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                rateLimitService,
                mock(SecurityProperties.class),
                new DeviceIdResolver(new CookieUtils(false, "", "Lax")),
                new ObjectMapper());

        mockMvc = MockMvcBuilders.standaloneSetup(bareFixture, new DeclaredHandlerFixture())
                .addInterceptors(interceptor)
                .build();

        interceptorLogger = (Logger) LoggerFactory.getLogger(RateLimitInterceptor.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        interceptorLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        interceptorLogger.detachAppender(logAppender);
    }

    @Test
    @DisplayName("An undeclared handler is denied with 500, logged by name, and its body never runs")
    void undeclaredHandler_WhenRequested_IsDeniedAndNeverReached() throws Exception {
        mockMvc.perform(get("/api/fixture/bare"))
                .andExpect(status().isInternalServerError());

        assertThat(bareFixture.bodyRan()).isFalse();
        verifyNoInteractions(rateLimitService);

        assertThat(logAppender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage())
                    .contains(BareHandlerFixture.class.getName() + ".bare");
        });
    }

    @Test
    @DisplayName("A handler inheriting its controller's policy is charged under that policy and runs")
    void inheritedPolicy_WhenRequested_IsChargedAndReached() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));

        mockMvc.perform(get("/api/fixture/inherited"))
                .andExpect(status().isOk());

        verify(rateLimitService).check(RateLimitPolicy.CRUD, 7L, "fixture");
        assertThat(logAppender.list).isEmpty();
    }

    @Test
    @DisplayName("A handler overriding its controller's policy with an exemption is charged nothing")
    void methodExemption_WhenRequested_OverridesTheClassPolicy() throws Exception {
        mockMvc.perform(get("/api/fixture/exempt"))
                .andExpect(status().isOk());

        verifyNoInteractions(rateLimitService);
        assertThat(logAppender.list).isEmpty();
    }
}
