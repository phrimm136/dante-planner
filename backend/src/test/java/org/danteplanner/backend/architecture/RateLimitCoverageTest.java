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
 * <p>The exemption is deliberately method-scoped in the codebase even where a whole controller is
 * exempt. A class-level exemption would silently cover handlers added to it later, which is the
 * omission this rule exists to catch.</p>
 */
@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class RateLimitCoverageTest {

    @ArchTest
    static final ArchRule request_handlers_declare_a_rate_limit_policy =
            RateLimitCoverageRule.REQUEST_HANDLERS_DECLARE_A_RATE_LIMIT_POLICY;
}
