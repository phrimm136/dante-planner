package org.danteplanner.backend.shared.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * Resolves {@link DeviceId} annotated parameters from HTTP-only cookies.
 *
 * <p>If the device ID cookie is not present, generates a new UUID and sets
 * it as an HTTP-only cookie with 1-year expiration.</p>
 */
@Component
@RequiredArgsConstructor
public class DeviceIdArgumentResolver implements HandlerMethodArgumentResolver {

    private static final int DEVICE_ID_MAX_AGE_SECONDS = 365 * 24 * 60 * 60;

    private final CookieUtils cookieUtils;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(DeviceId.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);

        if (request == null || response == null) {
            return UUID.randomUUID();
        }

        return getOrCreateDeviceId(request, response);
    }

    private UUID getOrCreateDeviceId(HttpServletRequest request, HttpServletResponse response) {
        UUID deviceId = readDeviceId(request);
        if (deviceId != null) {
            return deviceId;
        }

        UUID minted = UUID.randomUUID();
        cookieUtils.setCookie(
                response, CookieConstants.DEVICE_ID, minted.toString(), DEVICE_ID_MAX_AGE_SECONDS);
        return minted;
    }

    private UUID readDeviceId(HttpServletRequest request) {
        String value = cookieUtils.getCookieValue(request, CookieConstants.DEVICE_ID);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
