package org.danteplanner.backend.shared.ratelimit;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.bucket4j.TimeoutException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.LoginRedirect;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.exception.Problems;
import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;
import org.danteplanner.backend.shared.redis.RoleGated;
import org.danteplanner.backend.shared.util.ClientIpResolver;

/**
 * An interceptor runs inside the DispatcherServlet and therefore after the security filter chain,
 * where the authenticated principal is available.
 */
@Component
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String UNDECLARED_CODE = "RATE_LIMIT_UNDECLARED";
    private static final String CHARGE_SKIPPED_COUNTER = "rate_limit.charge_skipped";
    private static final String POLICY_TAG = "policy";
    private static final String BREAKER_OPEN = "Rate-limit store breaker open";

    private final RoleGated<RateLimitService> rateLimit;
    private final SecurityProperties securityProperties;
    private final DeviceIdResolver deviceIdResolver;
    private final FrontendProperties frontendProperties;
    private final ProblemWriter problemWriter;
    private final MeterRegistry meterRegistry;
    private final AtomicBoolean storeUnavailable = new AtomicBoolean();

    public RateLimitInterceptor(
            RateLimitService rateLimitService,
            SecurityProperties securityProperties,
            DeviceIdResolver deviceIdResolver,
            FrontendProperties frontendProperties,
            ProblemWriter problemWriter,
            MeterRegistry meterRegistry,
            RedisRoleHealth roleHealth) {
        this.rateLimit = new RoleGated<>(RedisRole.RATE_LIMIT, rateLimitService, roleHealth, meterRegistry);
        this.securityProperties = securityProperties;
        this.deviceIdResolver = deviceIdResolver;
        this.frontendProperties = frontendProperties;
        this.problemWriter = problemWriter;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // An SSE stream completing re-dispatches the same request through the same handler.
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }

        return switch (declarationOn(handlerMethod)) {
            case Declaration.Declared declared -> charge(declared.annotation(), request, response);
            case Declaration.Exempt exempt -> true;
            case Declaration.Undeclared undeclared -> deny(handlerMethod, request, response);
        };
    }

    private sealed interface Declaration {

        record Declared(RateLimited annotation) implements Declaration {
        }

        record Exempt() implements Declaration {
        }

        record Undeclared() implements Declaration {
        }
    }

    private static Declaration declarationOn(HandlerMethod handlerMethod) {
        RateLimited onMethod = handlerMethod.getMethodAnnotation(RateLimited.class);
        if (onMethod != null) {
            return new Declaration.Declared(onMethod);
        }
        if (handlerMethod.hasMethodAnnotation(RateLimitExempt.class)) {
            return new Declaration.Exempt();
        }
        RateLimited onType = handlerMethod.getBeanType().getAnnotation(RateLimited.class);
        return onType == null ? new Declaration.Undeclared() : new Declaration.Declared(onType);
    }

    private boolean charge(RateLimited declaration, HttpServletRequest request, HttpServletResponse response) {
        return rateLimit.call(service -> {
            try {
                chargeBucket(service, declaration, request, response);
                markStoreAvailable();
                return true;
            } catch (RedisException | TimeoutException unavailable) {
                warnStoreUnavailable(declaration, unavailable);
                return withoutStore(declaration, response, () -> unavailable);
            } catch (RateLimitExceededException refused) {
                if (declaration.denial() != RateLimitDenial.REDIRECT_LOGIN) {
                    throw refused;
                }
                log.warn("Rate limit exceeded on a browser-navigation endpoint: {}", refused.getMessage());
                return redirectToLogin(response, LoginRedirect.RATE_LIMITED);
            }
        }, () -> withoutStore(declaration, response, () -> new RedisConnectionException(BREAKER_OPEN)));
    }

    private boolean redirectToLogin(HttpServletResponse response, String loginRedirect) {
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader(HttpHeaders.LOCATION, frontendProperties.getUrl() + loginRedirect);
        return false;
    }

    private boolean withoutStore(
            RateLimited declaration, HttpServletResponse response, Supplier<RuntimeException> unavailable) {
        RateLimitPolicy policy = declaration.value();
        if (policy.failsClosed()) {
            if (declaration.denial() != RateLimitDenial.REDIRECT_LOGIN) {
                throw unavailable.get();
            }
            return redirectToLogin(response, LoginRedirect.UNAVAILABLE);
        }
        meterRegistry.counter(CHARGE_SKIPPED_COUNTER, POLICY_TAG, policy.name()).increment();
        return true;
    }

    private void warnStoreUnavailable(RateLimited declaration, RuntimeException unavailable) {
        RateLimitPolicy policy = declaration.value();
        if (policy.failsClosed()) {
            if (declaration.denial() == RateLimitDenial.REDIRECT_LOGIN) {
                log.warn("Rate-limit store unavailable on a browser-navigation endpoint: {}",
                        unavailable.getMessage());
            }
            return;
        }
        if (storeUnavailable.compareAndSet(false, true)) {
            log.warn("Rate-limit store unavailable, serving fail-open policies unmetered: {}",
                    unavailable.getMessage());
        } else {
            log.debug("Rate-limit store still unavailable, {} charge skipped: {}", policy, unavailable.getMessage());
        }
    }

    private void markStoreAvailable() {
        if (storeUnavailable.get()) {
            storeUnavailable.set(false);
        }
    }

    private void chargeBucket(RateLimitService rateLimitService, RateLimited declaration, HttpServletRequest request,
            HttpServletResponse response) {
        RateLimitPolicy policy = declaration.value();

        if (policy.subject() == RateLimitPolicy.Subject.CLIENT) {
            String identifier = ClientIpResolver.resolveClientIdentifier(
                    request, securityProperties, () -> deviceIdResolver.resolve(request, response));
            rateLimitService.check(policy, identifier);
            return;
        }

        long userId = authenticatedUserId();
        if (declaration.endpoint().isEmpty()) {
            rateLimitService.check(policy, userId);
        } else {
            rateLimitService.check(policy, userId, declaration.endpoint());
        }
    }

    private long authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new IllegalStateException(
                    "A user-keyed rate-limit policy was charged without an authenticated principal");
        }
        return userId;
    }

    private boolean deny(HandlerMethod handlerMethod, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        String handlerName = handlerMethod.getBeanType().getName()
                + "." + handlerMethod.getMethod().getName();
        log.error("Denying request: handler {} declares no rate-limit policy", handlerName);

        problemWriter.write(request, response,
                Problems.fill(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR),
                        UNDECLARED_CODE, "Request rejected"),
                new HttpHeaders());
        return false;
    }
}
