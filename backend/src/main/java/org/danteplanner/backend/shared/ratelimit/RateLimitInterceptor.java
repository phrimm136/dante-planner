package org.danteplanner.backend.shared.ratelimit;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.LoginRedirect;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.exception.RateLimitExceededException;
import org.danteplanner.backend.shared.service.RateLimitPolicy;
import org.danteplanner.backend.shared.service.RateLimitService;
import org.danteplanner.backend.shared.util.ClientIpResolver;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Charges a request against the rate-limit policy its handler declares, before the handler runs.
 *
 * <p>Runs inside the DispatcherServlet and therefore after the security filter chain, so the
 * authenticated principal is available to policies keyed by user.</p>
 *
 * <p>A handler that declares nothing is denied rather than passed through. The architecture rule
 * makes that state unreachable in a build that ran the tests; this branch covers the deploy where
 * it did not, because a gate is worth only what its last run proved.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String UNDECLARED_CODE = "RATE_LIMIT_UNDECLARED";

    private final RateLimitService rateLimitService;
    private final SecurityProperties securityProperties;
    private final DeviceIdResolver deviceIdResolver;
    private final FrontendProperties frontendProperties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // An SSE stream completing re-dispatches the same request through the same handler; the
        // charge belongs to the request, and it already happened on the initial dispatch.
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }

        RateLimited declaration = declarationOn(handlerMethod);
        if (declaration != null) {
            return charge(declaration, request, response);
        }
        if (isExempt(handlerMethod)) {
            return true;
        }
        return deny(handlerMethod, response);
    }

    /**
     * The declaration governing this handler, with a method-level one overriding its controller's.
     *
     * @return the governing {@link RateLimited}, or null when the handler is exempt or undeclared
     */
    private RateLimited declarationOn(HandlerMethod handlerMethod) {
        RateLimited onMethod = handlerMethod.getMethodAnnotation(RateLimited.class);
        if (onMethod != null) {
            return onMethod;
        }
        if (handlerMethod.hasMethodAnnotation(RateLimitExempt.class)) {
            return null;
        }
        return handlerMethod.getBeanType().getAnnotation(RateLimited.class);
    }

    private boolean isExempt(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(RateLimitExempt.class);
    }

    /**
     * @return true when the request may proceed, false when the refusal was already rendered
     * @throws RateLimitExceededException if the declaration asks for the refusal to be responded to
     */
    private boolean charge(RateLimited declaration, HttpServletRequest request, HttpServletResponse response) {
        try {
            chargeBucket(declaration, request, response);
            return true;
        } catch (RateLimitExceededException refused) {
            if (declaration.denial() != RateLimitDenial.REDIRECT_LOGIN) {
                throw refused;
            }
            log.warn("Rate limit exceeded on a browser-navigation endpoint: {}", refused.getMessage());
            response.setStatus(HttpStatus.FOUND.value());
            response.setHeader(HttpHeaders.LOCATION, frontendProperties.getUrl() + LoginRedirect.RATE_LIMITED);
            return false;
        }
    }

    private void chargeBucket(RateLimited declaration, HttpServletRequest request, HttpServletResponse response) {
        RateLimitPolicy policy = declaration.value();

        if (policy.subject() == RateLimitPolicy.Subject.CLIENT) {
            String identifier = ClientIpResolver.resolveClientIdentifier(
                    request, securityProperties, deviceIdResolver.resolve(request, response));
            rateLimitService.check(policy, identifier);
            return;
        }

        Long userId = authenticatedUserId();
        if (declaration.endpoint().isEmpty()) {
            rateLimitService.check(policy, userId);
        } else {
            rateLimitService.check(policy, userId, declaration.endpoint());
        }
    }

    /**
     * @throws IllegalStateException if the handler is reachable without authentication, which would
     *                               otherwise charge every anonymous caller to one shared bucket
     */
    private Long authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new IllegalStateException(
                    "A user-keyed rate-limit policy was charged without an authenticated principal");
        }
        return userId;
    }

    private boolean deny(HandlerMethod handlerMethod, HttpServletResponse response) throws IOException {
        String handlerName = handlerMethod.getBeanType().getName()
                + "." + handlerMethod.getMethod().getName();
        log.error("Denying request: handler {} declares no rate-limit policy", handlerName);

        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                new UndeclaredRateLimit(UNDECLARED_CODE, "Request rejected")));
        return false;
    }

    private record UndeclaredRateLimit(String code, String message) {
    }
}
