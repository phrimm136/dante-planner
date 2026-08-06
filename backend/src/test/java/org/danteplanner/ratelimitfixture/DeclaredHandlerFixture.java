package org.danteplanner.ratelimitfixture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.danteplanner.backend.shared.ratelimit.RateLimitExempt;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.danteplanner.backend.shared.service.RateLimitPolicy;

/**
 * Handlers covered by a class-level declaration, one of them overriding it with an exemption.
 *
 * <p>Outside {@code org.danteplanner.backend} on purpose: these fixtures are stereotyped
 * controllers, and inside the application's package they would be component-scanned into the
 * Spring context of every integration test, publishing fixture routes into real applications.</p>
 */
@RestController
@RequestMapping("/api/fixture")
@RateLimited(value = RateLimitPolicy.CRUD, endpoint = "fixture")
public class DeclaredHandlerFixture {

    @GetMapping("/inherited")
    public String inheritsTheClassPolicy() {
        return "reached";
    }

    @GetMapping("/exempt")
    @RateLimitExempt
    public String overridesWithAnExemption() {
        return "reached";
    }
}
