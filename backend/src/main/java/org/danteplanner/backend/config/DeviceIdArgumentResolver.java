package org.danteplanner.backend.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
public class DeviceIdArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String DEVICE_ID_COOKIE = "deviceId";
    private static final int DEVICE_ID_MAX_AGE = 365 * 24 * 60 * 60; // 1 year

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

    /**
     * Get device ID from cookie, or create and set a new one if not present.
     *
     * @param request  the HTTP request to read cookies from
     * @param response the HTTP response to set cookie on
     * @return the device ID as UUID
     */
    private UUID getOrCreateDeviceId(HttpServletRequest request, HttpServletResponse response) {
        UUID deviceId = getDeviceIdFromCookie(request);
        if (deviceId == null) {
            deviceId = UUID.randomUUID();
            setDeviceIdCookie(request, response, deviceId);
        }
        return deviceId;
    }

    /**
     * Read device ID from cookies.
     *
     * @param request the HTTP request
     * @return device ID as UUID or null if not present or invalid
     */
    private UUID getDeviceIdFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (DEVICE_ID_COOKIE.equals(cookie.getName())) {
                    try {
                        return UUID.fromString(cookie.getValue());
                    } catch (IllegalArgumentException e) {
                        // Invalid UUID format, will generate new one
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Set device ID as HTTP-only cookie.
     *
     * @param request  the HTTP request (to check if secure)
     * @param response the HTTP response
     * @param deviceId the device ID to set
     */
    private void setDeviceIdCookie(HttpServletRequest request, HttpServletResponse response, UUID deviceId) {
        Cookie cookie = new Cookie(DEVICE_ID_COOKIE, deviceId.toString());
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath("/");
        cookie.setMaxAge(DEVICE_ID_MAX_AGE);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }
}
