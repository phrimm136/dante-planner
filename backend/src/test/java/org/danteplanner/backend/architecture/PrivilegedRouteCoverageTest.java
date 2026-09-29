package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class PrivilegedRouteCoverageTest {

    @ArchTest
    static final ArchRule privileged_handlers_live_under_privileged_prefixes =
            PrivilegedRouteCoverageRule.PRIVILEGED_HANDLERS_LIVE_UNDER_PRIVILEGED_PREFIXES;
}
