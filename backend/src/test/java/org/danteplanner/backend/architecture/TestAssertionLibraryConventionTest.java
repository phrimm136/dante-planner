package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "org.danteplanner.backend", importOptions = ImportOption.OnlyIncludeTests.class)
class TestAssertionLibraryConventionTest {

    @ArchTest
    static final ArchRule assertj_only = JunitAssertionsRule.ASSERTJ_ONLY;
}
