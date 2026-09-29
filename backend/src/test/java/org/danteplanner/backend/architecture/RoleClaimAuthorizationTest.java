package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class RoleClaimAuthorizationTest {

    @ArchTest
    static final ArchRule only_the_authenticator_reads_the_role_claim =
            RoleClaimAuthorizationRule.ONLY_THE_AUTHENTICATOR_READS_THE_ROLE_CLAIM;

    @ArchTest
    static final ArchRule no_role_check_through_spring_security =
            RoleClaimAuthorizationRule.NO_ROLE_CHECK_THROUGH_SPRING_SECURITY;

    @ArchTest
    static final ArchRule only_the_security_chain_reads_granted_authorities =
            RoleClaimAuthorizationRule.ONLY_THE_SECURITY_CHAIN_READS_GRANTED_AUTHORITIES;
}
