package org.danteplanner.backend.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "org.danteplanner.backend")
class TestDisplayNameConventionTest {

    @ArchTest
    static final ArchRule tests_carry_no_display_name = DisplayNameRule.TESTS_CARRY_NO_DISPLAY_NAME;
}
