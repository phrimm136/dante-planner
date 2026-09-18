package org.danteplanner.backend.shared.config;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;

@Component
@RequiredArgsConstructor
public class DeviceIdResolver {

    private static final int DEVICE_ID_MAX_AGE_SECONDS = 365 * 24 * 60 * 60;

    private static final String REQUEST_ATTRIBUTE = DeviceIdResolver.class.getName();

    private final CookieUtils cookieUtils;

    public UUID resolve(HttpServletRequest request, HttpServletResponse response) {
        if (request.getAttribute(REQUEST_ATTRIBUTE) instanceof UUID memoised) {
            return memoised;
        }

        UUID deviceId = readDeviceId(request).orElseGet(() -> {
            UUID minted = UUID.randomUUID();
            cookieUtils.setCookie(
                    response, CookieConstants.DEVICE_ID, minted.toString(), DEVICE_ID_MAX_AGE_SECONDS);
            return minted;
        });

        request.setAttribute(REQUEST_ATTRIBUTE, deviceId);
        return deviceId;
    }

    private Optional<UUID> readDeviceId(HttpServletRequest request) {
        return cookieUtils.getCookieValue(request, CookieConstants.DEVICE_ID)
                .flatMap(DeviceIdResolver::parseUuid);
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
