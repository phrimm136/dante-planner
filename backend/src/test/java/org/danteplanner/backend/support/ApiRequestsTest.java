package org.danteplanner.backend.support;

import java.util.Arrays;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.shared.security.CsrfDoubleSubmitFilter;
import org.danteplanner.backend.shared.util.CookieConstants;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

class ApiRequestsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiRequests api = new ApiRequests(
            MockMvcBuilders.standaloneSetup(new EchoController()).build(), objectMapper);

    @Test
    void upsert_WhenGivenARequestAndCookies_PutsItsJsonToItsIdWithCsrfAndEveryCookie() throws Exception {
        UpsertPlannerRequest request = UpsertRequests.draft().title("Echoed").build();

        api.upsert(request, AuthCookies.accessToken("token"))
                .andExpect(content().string(request.id() + "|Echoed|csrf=true|"
                        + CookieConstants.ACCESS_TOKEN + "," + CookieConstants.CSRF));
    }

    @Test
    void get_WhenGivenNoCookies_SendsTheRequestWithoutAny() throws Exception {
        api.get("/echo").andExpect(content().string("cookies="));
    }

    @Test
    void get_WhenGivenCookies_SendsEachOne() throws Exception {
        api.get("/echo", AuthCookies.accessToken("token"), AuthCookies.deviceId("device"))
                .andExpect(content().string("cookies=" + CookieConstants.ACCESS_TOKEN + ","
                        + CookieConstants.DEVICE_ID));
    }

    @RestController
    static class EchoController {

        @PutMapping("/api/planner/md/{id}")
        String upsert(@PathVariable String id, @RequestBody UpsertPlannerRequest body, HttpServletRequest request) {
            boolean csrf = request.getHeader(CsrfDoubleSubmitFilter.CSRF_HEADER) != null;
            return id + "|" + body.title() + "|csrf=" + csrf + "|" + cookieNames(request);
        }

        @GetMapping("/echo")
        String echo(HttpServletRequest request) {
            return "cookies=" + cookieNames(request);
        }

        private static String cookieNames(HttpServletRequest request) {
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return "";
            }
            return String.join(",", Arrays.stream(cookies).map(Cookie::getName).toList());
        }
    }
}
