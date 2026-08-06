package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Freezes rate-limit coverage over every request handler in the codebase.
 *
 * <p>A limiter call placed by hand is invisible when it is absent, which is how two dozen handlers
 * came to be unguarded with nothing detecting it. Coverage is therefore a build failure rather
 * than a review habit: a handler that names neither a policy nor an exemption fails here.</p>
 *
 * <p>The exemption is method-scoped structurally — {@code @RateLimitExempt} targets methods only,
 * and this rule accepts it only there. A class-level exemption would cover handlers added to that
 * class later without anyone restating the decision, which is the omission this rule exists to
 * catch. A class-level policy is accepted because what it grants a later handler is protection.</p>
 *
 * <p>The shape rule beside it catches the two ways a declaration can be well-formed and still
 * wrong: a per-route policy left without an endpoint fails at runtime as a 500, and a policy that
 * names its own endpoint given one anyway silently respells a live bucket key.</p>
 */
@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class RateLimitCoverageTest {

    @ArchTest
    static final ArchRule request_handlers_declare_a_rate_limit_policy =
            RateLimitCoverageRule.REQUEST_HANDLERS_DECLARE_A_RATE_LIMIT_POLICY;

    @ArchTest
    static final ArchRule declarations_match_their_policy_shape =
            RateLimitCoverageRule.DECLARATIONS_MATCH_THEIR_POLICY_SHAPE;
}
