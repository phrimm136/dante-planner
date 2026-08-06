package org.danteplanner.backend.shared.config;

import java.util.UUID;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;

/**
 * Reads the caller's device id from its cookie, minting one when the cookie carries none.
 */
@Component
@RequiredArgsConstructor
public class DeviceIdResolver {

    private static final int DEVICE_ID_MAX_AGE_SECONDS = 365 * 24 * 60 * 60;

    private static final String REQUEST_ATTRIBUTE = DeviceIdResolver.class.getName();

    private final CookieUtils cookieUtils;

    /**
     * Resolves the device id this request is identified by.
     *
     * <p>The answer is memoised on the request because minting is a {@code Set-Cookie}: two mints
     * in one request would hand the caller two identities, and whichever cookie landed second
     * would silently orphan the bucket charged under the first.</p>
     *
     * @param request  the request whose device cookie is read
     * @param response the response a minted device id is set on
     * @return the caller's device id, never null
     */
    public UUID resolve(HttpServletRequest request, HttpServletResponse response) {
        if (request.getAttribute(REQUEST_ATTRIBUTE) instanceof UUID memoised) {
            return memoised;
        }

        UUID deviceId = readDeviceId(request);
        if (deviceId == null) {
            deviceId = UUID.randomUUID();
            cookieUtils.setCookie(
                    response, CookieConstants.DEVICE_ID, deviceId.toString(), DEVICE_ID_MAX_AGE_SECONDS);
        }

        request.setAttribute(REQUEST_ATTRIBUTE, deviceId);
        return deviceId;
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
