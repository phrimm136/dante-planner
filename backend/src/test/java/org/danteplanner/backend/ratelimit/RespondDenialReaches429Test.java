package org.danteplanner.backend.ratelimit;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.exception.GlobalExceptionHandler;
import org.danteplanner.backend.shared.ratelimit.RateLimitExceededException;
import org.danteplanner.backend.shared.ratelimit.RateLimitInterceptor;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.shared.ratelimit.RateLimitService;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.ratelimitfixture.DeclaredHandlerFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The default denial path: a refusal raised inside the interceptor still reaches the global handler
 * and renders as 429.
 *
 * <p>Every rate-limited handler but the OAuth callback relies on this. The charge used to happen
 * inside the handler, where the exception plainly reached the boundary; raising it from
 * {@code preHandle} routes it through the dispatcher's exception path instead, which is a different
 * mechanism and was until now only assumed to work.</p>
 */
class RespondDenialReaches429Test {

    private RateLimitService rateLimitService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(RateLimitService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                rateLimitService,
                mock(SecurityProperties.class),
                new DeviceIdResolver(new CookieUtils(false, "", "Lax")),
                new FrontendProperties("https://planner.example"),
                objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(new DeclaredHandlerFixture())
                .addInterceptors(interceptor)
                .setControllerAdvice(new GlobalExceptionHandler(new CookieUtils(false, "", "Lax"), objectMapper))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("An exhausted bucket on a RESPOND handler renders 429 RATE_LIMIT_EXCEEDED")
    void exhaustedBucket_WhenRespondHandlerRequested_Renders429() throws Exception {
        doThrow(new RateLimitExceededException(7L, "fixture"))
                .when(rateLimitService).check(eq(RateLimitPolicy.CRUD), eq(7L), anyString());

        mockMvc.perform(get("/api/fixture/inherited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("An available bucket lets the handler run")
    void availableBucket_WhenRespondHandlerRequested_ReachesTheHandler() throws Exception {
        mockMvc.perform(get("/api/fixture/inherited"))
                .andExpect(status().isOk());
    }
}
