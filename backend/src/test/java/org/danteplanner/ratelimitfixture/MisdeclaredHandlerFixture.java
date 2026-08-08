package org.danteplanner.ratelimitfixture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;

/**
 * Declarations that compile and read plausibly but name the wrong endpoint shape for their policy.
 *
 * <p>Outside {@code org.danteplanner.backend} on purpose: these fixtures are stereotyped
 * controllers, and inside the application's package they would be component-scanned into the
 * Spring context of every integration test, publishing fixture routes into real applications.</p>
 */
@RestController
@RequestMapping("/api/fixture")
public class MisdeclaredHandlerFixture {

    /** CRUD keys one bucket per route, so an unnamed endpoint leaves the charge with no bucket. */
    @GetMapping("/crud-without-endpoint")
    @RateLimited(RateLimitPolicy.CRUD)
    public String crudWithoutEndpoint() {
        return "reached";
    }

    /** SSE bakes its own endpoint into the key, so naming one here respells a live bucket. */
    @GetMapping("/self-endpoint-with-endpoint")
    @RateLimited(value = RateLimitPolicy.SSE, endpoint = "stream")
    public String selfEndpointPolicyNamingAnEndpoint() {
        return "reached";
    }
}
