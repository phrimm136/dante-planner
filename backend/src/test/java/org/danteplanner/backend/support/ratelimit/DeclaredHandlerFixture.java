package org.danteplanner.backend.support.ratelimit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import org.danteplanner.backend.shared.ratelimit.RateLimitExempt;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.danteplanner.backend.shared.service.RateLimitPolicy;

/**
 * Handlers covered by a class-level declaration, one of them overriding it with an exemption.
 *
 * <p>Deliberately not a {@code @RestController}: a stereotype here would be component-scanned into
 * the Spring context of every integration test.</p>
 */
@RequestMapping("/api/fixture")
@RateLimited(value = RateLimitPolicy.CRUD, endpoint = "fixture")
public class DeclaredHandlerFixture {

    @GetMapping("/inherited")
    @ResponseBody
    public String inheritsTheClassPolicy() {
        return "reached";
    }

    @GetMapping("/exempt")
    @RateLimitExempt
    @ResponseBody
    public String overridesWithAnExemption() {
        return "reached";
    }
}
