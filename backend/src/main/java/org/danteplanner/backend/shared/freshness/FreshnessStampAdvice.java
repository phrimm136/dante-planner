package org.danteplanner.backend.shared.freshness;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * <p>A JSON-bodied response is committed DURING serialization — the converter's generator flush
 * propagates to the servlet stream.</p>
 *
 * <p>MockMvc does not enforce commit semantics.</p>
 */
@ControllerAdvice
@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FreshnessStampAdvice implements ResponseBodyAdvice<Object> {

    private final MarkerStamp markerStamp;

    @Override
    public boolean supports(
        MethodParameter returnType,
        Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return true;
    }

    @Override
    public @Nullable Object beforeBodyWrite(
        @Nullable Object body,
        MethodParameter returnType,
        MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType,
        ServerHttpRequest request,
        ServerHttpResponse response
    ) {
        markerStamp.stamp();
        return body;
    }
}
