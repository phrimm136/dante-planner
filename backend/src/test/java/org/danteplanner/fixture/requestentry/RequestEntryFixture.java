package org.danteplanner.fixture.requestentry;

import java.security.Principal;
import java.util.function.Function;

import org.springframework.http.HttpEntity;
import org.springframework.http.RequestEntity;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @GetMapping("/security-context")
    public void securityContextHandler(@CurrentSecurityContext SecurityContext context) {
    }

    @GetMapping("/authentication")
    public void authenticationHandler(Authentication authentication) {
    }

    @GetMapping("/java-principal")
    public void javaPrincipalHandler(Principal principal) {
    }

    @GetMapping("/header")
    public void headerHandler(@RequestHeader("User-Agent") String agent) {
    }

    @PostMapping("/http-entity")
    public void httpEntityHandler(HttpEntity<String> entity) {
    }

    @PostMapping("/request-entity")
    public void requestEntityHandler(RequestEntity<String> entity) {
    }

    public Object headerValuesRead(HttpServletRequest request) {
        return request.getHeaders("User-Agent");
    }

    public Object headerNamesRead(HttpServletRequest request) {
        return request.getHeaderNames();
    }

    public int intHeaderRead(HttpServletRequest request) {
        return request.getIntHeader("Content-Length");
    }

    public long dateHeaderRead(HttpServletRequest request) {
        return request.getDateHeader("If-Modified-Since");
    }

    public Object serverRequestHeadersRead(ServerHttpRequest request) {
        return request.getHeaders();
    }

    public Object webRequestHeaderValuesRead(WebRequest request) {
        return request.getHeaderValues("User-Agent");
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
