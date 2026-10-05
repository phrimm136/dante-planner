package org.danteplanner.backend.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "org.danteplanner.backend")
class TestMockBeanConventionTest {

    @ArchTest
    static final ArchRule no_mockito_bean_overrides = MockBeanRule.NO_MOCKITO_BEAN_OVERRIDES;
}
