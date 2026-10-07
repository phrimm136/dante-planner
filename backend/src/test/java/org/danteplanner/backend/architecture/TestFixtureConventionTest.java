package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "org.danteplanner.backend", importOptions = ImportOption.OnlyIncludeTests.class)
class TestFixtureConventionTest {

    @ArchTest
    static final ArchRule fixtures_only_in_support = FixtureConstructionRule.FIXTURES_ONLY_IN_SUPPORT;
}
