package org.danteplanner.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.DispatcherType;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MdcLoggingFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private MdcLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new MdcLoggingFilter();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Nested
    @DisplayName("shouldNotFilter")
    class ShouldNotFilterTests {

        @Test
        @DisplayName("Returns true for ASYNC dispatch — avoids double MDC population on SSE continuations")
        void shouldNotFilter_asyncDispatch_returnsTrue() {
            when(request.getDispatcherType()).thenReturn(DispatcherType.ASYNC);

            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("Returns false for normal REQUEST dispatch")
        void shouldNotFilter_normalRequest_returnsFalse() {
            when(request.getDispatcherType()).thenReturn(DispatcherType.REQUEST);

            assertThat(filter.shouldNotFilter(request)).isFalse();
        }

        @Test
        @DisplayName("Returns false for ERROR dispatch — generates fresh MDC for error page rendering")
        void shouldNotFilter_errorDispatch_returnsFalse() {
            when(request.getDispatcherType()).thenReturn(DispatcherType.ERROR);

            assertThat(filter.shouldNotFilter(request)).isFalse();
        }
    }

    @Nested
    @DisplayName("MDC population")
    class MdcPopulationTests {

        @Test
        @DisplayName("Guest request — userId is 'guest'")
        void doFilterInternal_noAuthentication_setsGuestUserId() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/planner/md/published");

            String[] capturedUserId = new String[1];
            doAnswer(invocation -> {
                capturedUserId[0] = MDC.get("userId");
                return null;
            }).when(filterChain).doFilter(request, response);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedUserId[0]).isEqualTo("guest");
        }

        @Test
        @DisplayName("Authenticated request — userId matches Long principal")
        void doFilterInternal_authenticatedUser_setsNumericUserId() throws Exception {
            Long userId = 42L;
            var auth = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_NORMAL"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/api/planner");

            String[] capturedUserId = new String[1];
            doAnswer(invocation -> {
                capturedUserId[0] = MDC.get("userId");
                return null;
            }).when(filterChain).doFilter(request, response);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedUserId[0]).isEqualTo("42");
        }

        @Test
        @DisplayName("AnonymousAuthenticationToken — userId is 'guest', not 'anonymousUser'")
        void doFilterInternal_springAnonymousToken_setsGuestUserId() throws Exception {
            var anon = new AnonymousAuthenticationToken(
                    "key", "anonymousUser",
                    List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
            );
            SecurityContextHolder.getContext().setAuthentication(anon);

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/public/health");

            String[] capturedUserId = new String[1];
            doAnswer(invocation -> {
                capturedUserId[0] = MDC.get("userId");
                return null;
            }).when(filterChain).doFilter(request, response);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedUserId[0]).isEqualTo("guest");
        }

        @Test
        @DisplayName("Populates method and path from request")
        void doFilterInternal_setsMethodAndPath() throws Exception {
            when(request.getMethod()).thenReturn("DELETE");
            when(request.getRequestURI()).thenReturn("/api/planner/123");

            String[] capturedMethod = new String[1];
            String[] capturedPath = new String[1];
            doAnswer(invocation -> {
                capturedMethod[0] = MDC.get("method");
                capturedPath[0] = MDC.get("path");
                return null;
            }).when(filterChain).doFilter(request, response);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedMethod[0]).isEqualTo("DELETE");
            assertThat(capturedPath[0]).isEqualTo("/api/planner/123");
        }

        @Test
        @DisplayName("Strips CR and LF from URI — prevents log injection via crafted paths")
        void doFilterInternal_crlfInUri_sanitizesPath() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/planner\r\nFAKE LOG ENTRY");

            String[] capturedPath = new String[1];
            doAnswer(invocation -> {
                capturedPath[0] = MDC.get("path");
                return null;
            }).when(filterChain).doFilter(request, response);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedPath[0])
                    .doesNotContain("\r")
                    .doesNotContain("\n")
                    .isEqualTo("/api/planner__FAKE LOG ENTRY");
        }

    }

    @Nested
    @DisplayName("MDC cleanup")
    class MdcCleanupTests {

        @Test
        @DisplayName("Clears MDC after successful request — verifies it was populated then cleaned up")
        void doFilterInternal_clearsAfterChain() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/planner/md/published");

            boolean[] wasPopulatedDuringChain = {false};
            doAnswer(invocation -> {
                wasPopulatedDuringChain[0] = MDC.get("path") != null;
                return null;
            }).when(filterChain).doFilter(request, response);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(wasPopulatedDuringChain[0]).isTrue();
            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        }

        @Test
        @DisplayName("Clears MDC even when filter chain throws — finally block guarantees cleanup")
        void doFilterInternal_clearsOnException() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/planner/md/published");
            doThrow(new ServletException("downstream failure"))
                    .when(filterChain).doFilter(request, response);

            assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                    .isInstanceOf(ServletException.class);

            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        }
    }
}
