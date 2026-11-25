package org.danteplanner.backend.security;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.support.TestJsonMappers;
import org.danteplanner.backend.shared.security.CsrfDoubleSubmitFilter;

import org.danteplanner.backend.shared.exception.ProblemWriter;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;

import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.shared.security.CsrfTokenService;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;

import ch.qos.logback.classic.Level;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CsrfDoubleSubmitFilter}: every state-changing request carries an
 * {@code X-CSRF-Token} equal to the {@code csrf} cookie, or is 403.
 */
class CsrfDoubleSubmitFilterTest {

    private CsrfDoubleSubmitFilter filter;
    private CsrfTokenService expiredMinter;
    private String token;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        JwtProperties jwtProperties = new JwtProperties(null, null, key, 900000L, 604800000L);
        CsrfTokenService csrfTokenService = new CsrfTokenService(jwtProperties, Clock.systemUTC());
        token = csrfTokenService.mint();
        expiredMinter = new CsrfTokenService(jwtProperties,
                Clock.offset(Clock.systemUTC(), Duration.ofDays(-8)));

        CookieUtils cookieUtils = new CookieUtils(true, "", "Lax");
        filter = new CsrfDoubleSubmitFilter(
                cookieUtils,
                new ProblemWriter(TestJsonMappers.springDefaults()),
                csrfTokenService);
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
    class Enforcement {

        @Test
        void unsafeMethod_WhenMissingHeader_Rejected() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");
            req.setCookies(csrfCookie(token));
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(chain.getRequest()).isNull();
            assertThat(res.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            String body = res.getContentAsString();
            assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("CSRF_TOKEN_INVALID");
            assertThat((String) JsonPath.read(body, "$.detail"))
                    .isEqualTo("Missing or invalid CSRF token");
            assertThat((String) JsonPath.read(body, "$.message"))
                    .isEqualTo("Missing or invalid CSRF token");
        }

        @Test
        void unsafeMethod_WhenMismatchedHeader_Rejected() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");
            req.setCookies(csrfCookie(token));
            req.addHeader(CsrfDoubleSubmitFilter.CSRF_HEADER, "different-token");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        void unsafeMethod_WhenMatchingToken_Passes() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");
            req.setCookies(csrfCookie(token));
            req.addHeader(CsrfDoubleSubmitFilter.CSRF_HEADER, token);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(chain.getRequest()).isSameAs(req);
        }

        @Test
        void otherUnsafeMethods_WhenMissingHeader_Rejected() throws Exception {
            for (String method : new String[]{"PUT", "PATCH", "DELETE"}) {
                MockHttpServletRequest req = request(method, "/api/planner/md/1");
                req.setCookies(csrfCookie(token));
                MockHttpServletResponse res = new MockHttpServletResponse();
                MockFilterChain chain = new MockFilterChain();

                filter.doFilter(req, res, chain);

                assertThat(res.getStatus()).as(method).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
                assertThat(chain.getRequest()).as(method).isNull();
            }
        }
    }

    @Nested
    class CookieReissue {

        private MockHttpServletResponse responseTo(String method, String cookie, String header) throws Exception {
            MockHttpServletRequest req = request(method, "/api/planner/md/1");
            if (cookie != null) {
                req.setCookies(csrfCookie(cookie));
            }
            if (header != null) {
                req.addHeader(CsrfDoubleSubmitFilter.CSRF_HEADER, header);
            }
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            return res;
        }

        private void assertReissued(MockHttpServletResponse res) {
            Cookie reissued = responseCsrfCookie(res);
            assertThat(reissued).isNotNull();
            assertThat(reissued.getValue()).isNotEqualTo(token);
            assertThat(reissued.getMaxAge()).isEqualTo(604800);
            assertThat(reissued.getPath()).isEqualTo("/");
            assertThat(reissued.getAttribute("SameSite")).isEqualTo("Lax");
        }

        private void assertCsrfTokenInvalid(MockHttpServletResponse res) throws Exception {
            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat((String) JsonPath.read(res.getContentAsString(), "$.code")).isEqualTo("CSRF_TOKEN_INVALID");
        }

        @Test
        void serverIssuedCookie_WhenPresented_IsNeverReissued() throws Exception {
            assertThat(responseCsrfCookie(responseTo("GET", token, null))).isNull();
            assertThat(responseCsrfCookie(responseTo("POST", token, token))).isNull();
            assertThat(responseCsrfCookie(responseTo("POST", token, null))).isNull();
            assertThat(responseCsrfCookie(responseTo("POST", token, "different-token"))).isNull();
        }

        @Test
        void expiredCookie_WhenEchoed_IsReissuedAndRejected() throws Exception {
            String expired = expiredMinter.mint();

            MockHttpServletResponse res = responseTo("POST", expired, expired);

            assertCsrfTokenInvalid(res);
            assertReissued(res);
        }

        @Test
        void unrecognizedCookie_WhenEchoed_IsReissuedAndRejected() throws Exception {
            String forgedMac = token.substring(0, token.lastIndexOf('.') + 1) + "AAAA";
            for (String planted : new String[]{"", ".", "a", "a.b.!", ".AAAA", forgedMac}) {
                MockHttpServletResponse res = responseTo("POST", planted, planted);

                assertCsrfTokenInvalid(res);
                assertReissued(res);
            }
        }

        @Test
        void unrecognizedCookie_WhenTheMethodIsSafe_IsReissuedAndPasses() throws Exception {
            for (String method : new String[]{"GET", "HEAD", "OPTIONS"}) {
                MockHttpServletResponse res = responseTo(method, expiredMinter.mint(), null);

                assertThat(res.getStatus()).as(method).isEqualTo(HttpServletResponse.SC_OK);
                assertReissued(res);
            }
        }

        @Test
        void missingCookie_WhenTheHeaderIsSent_IsReissuedAndRejected() throws Exception {
            MockHttpServletResponse res = responseTo("POST", null, token);

            assertCsrfTokenInvalid(res);
            assertReissued(res);
        }
    }

    @Nested
    class RejectionLog {

        private static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64)";

        private String rejectionLine(MockHttpServletRequest req) throws Exception {
            return rejectionLine(req, USER_AGENT);
        }

        private String rejectionLine(MockHttpServletRequest req, String userAgent) throws Exception {
            req.addHeader(HttpHeaders.USER_AGENT, userAgent);
            try (LogCapture log = LogCapture.onThisThread(CsrfDoubleSubmitFilter.class)) {
                filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
                assertThat(log.at(Level.WARN)).hasSize(1);
                return log.at(Level.WARN).getFirst().getFormattedMessage();
            }
        }

        @Test
        void mutation_WhenNoCookie_LogsNoCookie() throws Exception {
            MockHttpServletRequest req = request("PUT", "/api/planner/md/1");

            assertThat(rejectionLine(req)).isEqualTo(
                    "CSRF validation failed: reason=NO_COOKIE method=PUT path=/api/planner/md/1 userAgent="
                            + USER_AGENT);
        }

        @Test
        void mutation_WhenCookieNotServerIssued_LogsInvalidCookie() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");
            req.setCookies(csrfCookie("attacker-planted-value"));
            req.addHeader(CsrfDoubleSubmitFilter.CSRF_HEADER, "attacker-planted-value");

            assertThat(rejectionLine(req)).contains("reason=INVALID_COOKIE");
        }

        @Test
        void mutation_WhenCookieEmptyOrExpired_LogsInvalidCookie() throws Exception {
            for (String cookie : new String[]{"", expiredMinter.mint()}) {
                MockHttpServletRequest req = request("POST", "/api/planner/md/1");
                req.setCookies(csrfCookie(cookie));

                assertThat(rejectionLine(req)).contains("reason=INVALID_COOKIE");
            }
        }

        @Test
        void mutation_WhenHeaderEmpty_LogsNoHeader() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");
            req.setCookies(csrfCookie(token));
            req.addHeader(CsrfDoubleSubmitFilter.CSRF_HEADER, "");

            assertThat(rejectionLine(req)).contains("reason=NO_HEADER");
        }

        @Test
        void mutation_WhenHeaderMissing_LogsNoHeader() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");
            req.setCookies(csrfCookie(token));

            assertThat(rejectionLine(req)).contains("reason=NO_HEADER");
        }

        @Test
        void mutation_WhenHeaderDiffers_LogsMismatch() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");
            req.setCookies(csrfCookie(token));
            req.addHeader(CsrfDoubleSubmitFilter.CSRF_HEADER, "different-token");

            assertThat(rejectionLine(req)).contains("reason=MISMATCH");
        }

        @Test
        void mutation_WhenUserAgentCarriesLineBreaks_LogsOneLine() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");

            assertThat(rejectionLine(req, "agent\r\nforged"))
                    .endsWith("userAgent=agent__forged")
                    .doesNotContain("\n");
        }
    }

    @Nested
    class Exempt {

        @Test
        void safeMethod_WhenNoToken_NeverBlocked() throws Exception {
            MockHttpServletRequest req = request("GET", "/api/planner/md/1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(chain.getRequest()).isSameAs(req);
        }

    }

    @Nested
    class GuestBootstrap {

        @Test
        void safeMethod_WhenNoCsrfCookie_SetsCookieOnResponse() throws Exception {
            MockHttpServletRequest req = request("GET", "/api/planner/md/recommended");
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
        void guestMutation_WhenNoCsrfCookie_SetsCookieThenRejects() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(chain.getRequest()).isNull();
            assertThat(responseCsrfCookie(res)).isNotNull();
        }

        @Test
        void retryWithEchoedToken_WhenCookieAndHeaderMatch_Passes() throws Exception {
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

        @Test
        void mutation_WhenTokenNotServerIssued_Rejected() throws Exception {
            String attackerChosen = "attacker-planted-value";
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");
            req.setCookies(csrfCookie(attackerChosen));
            req.addHeader(CsrfDoubleSubmitFilter.CSRF_HEADER, attackerChosen);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        void mutation_WhenTokenTampered_Rejected() throws Exception {
            String tampered = (token.charAt(0) == 'x' ? 'y' : 'x') + token.substring(1);
            assertThat(tampered).isNotEqualTo(token);
            MockHttpServletRequest req = request("POST", "/api/planner/md/1");
            req.setCookies(csrfCookie(tampered));
            req.addHeader(CsrfDoubleSubmitFilter.CSRF_HEADER, tampered);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(chain.getRequest()).isNull();
        }
    }
}
