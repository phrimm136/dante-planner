package org.danteplanner.backend.support.ratelimit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * A request handler carrying no rate-limit declaration — the shape the coverage rule rejects and
 * the runtime backstop denies.
 *
 * <p>Deliberately not a {@code @RestController}: a stereotype here would be component-scanned into
 * the Spring context of every integration test.</p>
 */
@RequestMapping("/api/fixture")
public class BareHandlerFixture {

    private boolean bodyRan;

    @GetMapping("/bare")
    @ResponseBody
    public String bare() {
        bodyRan = true;
        return "reached";
    }

    public boolean bodyRan() {
        return bodyRan;
    }
}
