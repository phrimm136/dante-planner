package org.danteplanner.ratelimitfixture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A request handler carrying no rate-limit declaration — the shape the coverage rule rejects and
 * the runtime backstop denies.
 *
 * <p>Outside {@code org.danteplanner.backend} on purpose: these fixtures are stereotyped
 * controllers, and inside the application's package they would be component-scanned into the
 * Spring context of every integration test, publishing fixture routes into real applications.</p>
 */
@RestController
@RequestMapping("/api/fixture")
public class BareHandlerFixture {

    private boolean bodyRan;

    @GetMapping("/bare")
    public String bare() {
        bodyRan = true;
        return "reached";
    }

    public boolean bodyRan() {
        return bodyRan;
    }
}
