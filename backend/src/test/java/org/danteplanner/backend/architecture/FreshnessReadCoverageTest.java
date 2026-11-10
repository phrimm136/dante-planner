package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class FreshnessReadCoverageTest {

    @ArchTest
    static final ArchRule read_only_methods_declare_exactly_one_freshness_read =
            FreshnessReadCoverageRule.READ_ONLY_METHODS_DECLARE_EXACTLY_ONE_FRESHNESS_READ;
}
