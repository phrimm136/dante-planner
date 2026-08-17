package org.danteplanner.backend.shared.gtid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import lombok.RequiredArgsConstructor;

/**
 * Mints the {@link GtidCookie#NAME} cookie before the response body is written.
 *
 * <p>{@link GtidCookieFilter} adds the cookie after the chain returns, but a JSON-bodied response
 * is committed DURING serialization — the converter's generator flush propagates to the servlet
 * stream — and a header added to a committed response is silently dropped. So on every bodied
 * write the cookie must be attached here, after the controller (and every {@code AFTER_COMMIT}
 * listener, whose {@code REQUIRES_NEW} commits the accumulator already holds) but before the
 * first body byte. MockMvc does not enforce commit semantics, which is why the servlet-container
 * behavior is invisible to the MockMvc tier.</p>
 */
@ControllerAdvice
@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class GtidCookieResponseAdvice implements ResponseBodyAdvice<Object> {

    private final GtidWriteCapture writeCapture;

    @Override
    public boolean supports(
        MethodParameter returnType,
        Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
        Object body,
        MethodParameter returnType,
        MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType,
        ServerHttpRequest request,
        ServerHttpResponse response
    ) {
        writeCapture.takeCapturedGtid()
            .ifPresent(gtid ->
                response.getHeaders().add(HttpHeaders.SET_COOKIE, GtidCookie.of(gtid).toString())
            );
        return body;
    }
}
