package org.danteplanner.fixture.requestentry;

import java.util.function.Function;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class RequestEntryFixture {

    @GetMapping("/principal")
    public void principalHandler(@AuthenticationPrincipal Long userId) {
    }

    public String headerRead(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    public String wrappedHeaderRead(HttpServletRequestWrapper request) {
        return request.getHeader("User-Agent");
    }

    public Object headerReadByReference(HttpServletRequest request) {
        Function<String, String> reader = request::getHeader;
        return reader;
    }

    public String webRequestHeaderRead(WebRequest request) {
        return request.getHeader("User-Agent");
    }

    public String responseHeaderRead(HttpServletResponse response) {
        return response.getHeader("Location");
    }
}
