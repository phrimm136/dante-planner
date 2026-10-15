package org.danteplanner.backend.shared.gtid;

import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.MarkerStore;
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
 * <p>A JSON-bodied response is committed DURING serialization — the converter's generator flush
 * propagates to the servlet stream — and a header added to a committed response is silently
 * dropped.</p>
 *
 * <p>MockMvc does not enforce commit semantics.</p>
 */
@ControllerAdvice
@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class GtidCookieResponseAdvice implements ResponseBodyAdvice<Object> {

    private final GtidWriteCapture writeCapture;
    private final MarkerStore markerStore;

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
            .ifPresent(gtid -> {
                markerStore.raise(FreshnessDeclarations.declared(), gtid);
                response.getHeaders().add(HttpHeaders.SET_COOKIE, GtidCookie.of(gtid).toString());
            });
        return body;
    }
}
