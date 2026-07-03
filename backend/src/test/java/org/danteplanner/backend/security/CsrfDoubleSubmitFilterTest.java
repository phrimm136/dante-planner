package org.danteplanner.backend.security;
import org.danteplanner.backend.shared.security.CsrfDoubleSubmitFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;

import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CsrfDoubleSubmitFilter} — realizes INV3 (every state-changing
 * request carries an {@code X-CSRF-Token} equal to the {@code csrf} cookie, or is 403).
 */
@DisplayName("CsrfDoubleSubmitFilter")
class CsrfDoubleSubmitFilterTest {

    private static final String TOKEN = "valid-csrf-token";

    private CsrfDoubleSubmitFilter filter;

    @BeforeEach
    void setUp() {
        CookieUtils cookieUtils = new CookieUtils(true, "", "Lax");
        filter = new CsrfDoubleSubmitFilter(cookieUtils, new ObjectMapper());
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setDispatcherType(DispatcherType.REQUEST);
        return req;
    }

    private Cookie csrfCookie(String value) {
        return new Cookie(CookieConstants.CSRF, value);
    }

    private Cookie responseCsrfCookie(MockHttpServletResponse response) {
        return response.getCookie(CookieConstants.CSRF);
    }

    @Nested
    @DisplayName("Enforcement on unsafe methods")
    class Enforcement {

        @Test
        @DisplayName("POST with cookie but no header → 403, chain not continued")
        void unsafeMethod_missingHeader_rejected() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");
            req.setCookies(csrfCookie(TOKEN));
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("POST with mismatched header → 403, chain not continued")
        void unsafeMethod_mismatchedHeader_rejected() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");
            req.setCookies(csrfCookie(TOKEN));
            req.addHeader(CsrfDoubleSubmitFilter.CSRF_HEADER, "different-token");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("POST with matching cookie + header → passes through")
        void unsafeMethod_matchingToken_passes() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");
            req.setCookies(csrfCookie(TOKEN));
            req.addHeader(CsrfDoubleSubmitFilter.CSRF_HEADER, TOKEN);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(chain.getRequest()).isSameAs(req);
        }

        @Test
        @DisplayName("PUT/PATCH/DELETE are enforced like POST")
        void otherUnsafeMethods_whenMissingHeader_rejected() throws Exception {
            for (String method : new String[]{"PUT", "PATCH", "DELETE"}) {
                MockHttpServletRequest req = request(method, "/api/planner/md/1");
                req.setCookies(csrfCookie(TOKEN));
                MockHttpServletResponse res = new MockHttpServletResponse();
                MockFilterChain chain = new MockFilterChain();

                filter.doFilter(req, res, chain);

                assertThat(res.getStatus()).as(method).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
                assertThat(chain.getRequest()).as(method).isNull();
            }
        }
    }

    @Nested
    @DisplayName("Exempt requests")
    class Exempt {

        @Test
        @DisplayName("GET is never blocked, even without any token")
        void safeMethod_whenNoToken_neverBlocked() throws Exception {
            MockHttpServletRequest req = request("GET", "/api/planner/md/1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(chain.getRequest()).isSameAs(req);
        }

        @Test
        @DisplayName("POST to /api/internal/** is not blocked (machine-to-machine)")
        void internalEndpoint_whenUnsafeMethod_notBlocked() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/internal/refresh-game-data");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(chain.getRequest()).isSameAs(req);
        }
    }

    @Nested
    @DisplayName("Guest bootstrap (ensure-cookie)")
    class GuestBootstrap {

        @Test
        @DisplayName("Request with no csrf cookie receives a Set-Cookie for csrf")
        void safeMethod_whenNoCsrfCookie_setsCookieOnResponse() throws Exception {
            MockHttpServletRequest req = request("GET", "/api/planner/md/config");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            Cookie set = responseCsrfCookie(res);
            assertThat(set).isNotNull();
            assertThat(set.getValue()).isNotBlank();
            assertThat(set.isHttpOnly()).isFalse();
            assertThat(set.getSecure()).isTrue();
        }

        @Test
        @DisplayName("Guest mutation: Set-Cookie issued AND request rejected 403")
        void guestMutation_whenNoCsrfCookie_setsCookieThenRejects() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(chain.getRequest()).isNull();
            assertThat(responseCsrfCookie(res)).isNotNull();
        }

        @Test
        @DisplayName("Retry with echoed cookie + header passes")
        void retryWithEchoedToken_whenCookieAndHeaderMatch_passes() throws Exception {
            MockHttpServletRequest first = request("POST", "/api/planner/md/1");
            MockHttpServletResponse firstRes = new MockHttpServletResponse();
            filter.doFilter(first, firstRes, new MockFilterChain());
            String issued = responseCsrfCookie(firstRes).getValue();

            MockHttpServletRequest retry = request("POST", "/api/planner/md/1");
            retry.setCookies(csrfCookie(issued));
            retry.addHeader(CsrfDoubleSubmitFilter.CSRF_HEADER, issued);
            MockHttpServletResponse retryRes = new MockHttpServletResponse();
            MockFilterChain retryChain = new MockFilterChain();

            filter.doFilter(retry, retryRes, retryChain);

            assertThat(retryRes.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(retryChain.getRequest()).isSameAs(retry);
        }
    }
}
