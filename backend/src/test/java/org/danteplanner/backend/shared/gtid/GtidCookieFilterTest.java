package org.danteplanner.backend.shared.gtid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;

import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.RoutingKey;

class GtidCookieFilterTest {

    private static final String GTID = "3E11FA47-71CA-11E1-9E33-C80AA9429562:1-5";

    private final GtidReadGate readGate = mock(GtidReadGate.class);
    private final GtidWriteCapture writeCapture = mock(GtidWriteCapture.class);
    private final GtidCookieFilter filter = new GtidCookieFilter(readGate, writeCapture);

    @Test
    void readGet_WhenNoCookie_PassesThroughWithoutGateOrPin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<ReadOnlyRoutingDataSource> ds = mockStatic(ReadOnlyRoutingDataSource.class)) {
            filter.doFilter(request, response, new MockFilterChain());

            ds.verify(() -> ReadOnlyRoutingDataSource.pinTo(any()), never());
        }

        verifyNoInteractions(readGate);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void readGet_WhenCookieCaughtUp_ClearsCookieAndDoesNotPin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        request.setCookies(new Cookie(GtidCookie.NAME, GtidCookie.of(GTID).getValue()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(readGate.isCaughtUp(GTID)).thenReturn(true);

        try (MockedStatic<ReadOnlyRoutingDataSource> ds = mockStatic(ReadOnlyRoutingDataSource.class)) {
            filter.doFilter(request, response, new MockFilterChain());

            ds.verify(() -> ReadOnlyRoutingDataSource.pinTo(any()), never());
        }

        verify(readGate).isCaughtUp(GTID);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .contains(GtidCookie.NAME + "=")
                        .contains("Max-Age=0"));
    }

    @Test
    void readGet_WhenCookieNotCaughtUp_PinsPrimaryRetainsCookieAndClearsPin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        request.setCookies(new Cookie(GtidCookie.NAME, GtidCookie.of(GTID).getValue()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(readGate.isCaughtUp(GTID)).thenReturn(false);

        try (MockedStatic<ReadOnlyRoutingDataSource> ds = mockStatic(ReadOnlyRoutingDataSource.class)) {
            filter.doFilter(request, response, new MockFilterChain());

            ds.verify(() -> ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY));
            ds.verify(ReadOnlyRoutingDataSource::clear);
        }

        verify(readGate).isCaughtUp(GTID);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void readGet_WhenNotCaughtUpAndChainThrows_StillClearsPin() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        request.setCookies(new Cookie(GtidCookie.NAME, GtidCookie.of(GTID).getValue()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(readGate.isCaughtUp(GTID)).thenReturn(false);
        RuntimeException boom = new RuntimeException("chain blew up");
        FilterChain throwingChain = (req, res) -> {
            throw boom;
        };

        try (MockedStatic<ReadOnlyRoutingDataSource> ds = mockStatic(ReadOnlyRoutingDataSource.class)) {
            assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain))
                    .isSameAs(boom);

            ds.verify(ReadOnlyRoutingDataSource::clear);
        }
    }

    @Test
    void write_WhenCaptureHasGtid_SetsGtidCookieAndSkipsReadGate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/planners");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(writeCapture.pollCapturedGtid()).thenReturn(Optional.of(GTID));

        try (MockedStatic<ReadOnlyRoutingDataSource> ds = mockStatic(ReadOnlyRoutingDataSource.class)) {
            filter.doFilter(request, response, new MockFilterChain());

            ds.verify(() -> ReadOnlyRoutingDataSource.pinTo(any()), never());
        }

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .contains(GtidCookie.NAME + "=" + GtidCookie.of(GTID).getValue())
                        .contains("HttpOnly")
                        .contains("Secure")
                        .contains("SameSite=Lax"));
        verifyNoInteractions(readGate);
    }

    @Test
    void write_WhenCaptureHasMultiUuidGtidSet_SetsCookieWithoutError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/planners");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String gtidSet = "3e11fa47-71ca-11e1-9e33-c80aa9429562:1-100,"
                + "8f9e0d1c-2b3a-4c5d-6e7f-8a9b0c1d2e3f:1-50";
        when(writeCapture.pollCapturedGtid()).thenReturn(Optional.of(gtidSet));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .contains(GtidCookie.NAME + "=" + GtidCookie.of(gtidSet).getValue()));
    }

    @Test
    void readGet_WhenCookieValueUndecodable_TreatedAsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        request.setCookies(new Cookie(GtidCookie.NAME, "not*base64url*value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<ReadOnlyRoutingDataSource> ds = mockStatic(ReadOnlyRoutingDataSource.class)) {
            filter.doFilter(request, response, new MockFilterChain());

            ds.verify(() -> ReadOnlyRoutingDataSource.pinTo(any()), never());
        }

        verifyNoInteractions(readGate);
    }
}
