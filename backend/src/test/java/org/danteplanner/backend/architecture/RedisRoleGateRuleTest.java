package org.danteplanner.backend.architecture;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import org.danteplanner.backend.architecture.fixture.GatedStoreReadingFixture;
import org.danteplanner.backend.architecture.fixture.RoleHealthReadingFixture;
import org.danteplanner.backend.architecture.fixture.RoleHealthReferencingFixture;
import org.danteplanner.backend.architecture.fixture.StoreHandleHoldingFixture;
import org.danteplanner.backend.architecture.fixture.StoreHandleInjectedFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisRoleGateRuleTest {

    @Test
    void onlyTheGateReadsRoleHealth_WhenAnotherClassAsksForARolesState_FailsNamingTheCall() {
        assertThatThrownBy(() -> RedisRoleGateRule.ONLY_THE_GATE_READS_ROLE_HEALTH.check(
                imported(RoleHealthReadingFixture.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(RoleHealthReadingFixture.class.getName() + ".isAuthUp(")
                .hasMessageContaining("isAvailable");
    }

    @Test
    void onlyTheGateReadsRoleHealth_WhenAnotherClassReferencesTheCheck_FailsNamingTheReference() {
        assertThatThrownBy(() -> RedisRoleGateRule.ONLY_THE_GATE_READS_ROLE_HEALTH.check(
                imported(RoleHealthReferencingFixture.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(RoleHealthReferencingFixture.class.getName() + ".availability(")
                .hasMessageContaining("references method")
                .hasMessageContaining("isAvailable");
    }

    @Test
    void onlyTheGateReadsRoleHealth_WhenProductionIsChecked_Passes() {
        RedisRoleGateRule.ONLY_THE_GATE_READS_ROLE_HEALTH.check(ImportedClasses.production());
    }

    @Test
    void storeHandlesStayBehindTheGate_WhenAClassKeepsATemplateInAField_FailsNamingTheField() {
        assertThatThrownBy(() -> RedisRoleGateRule.STORE_HANDLES_STAY_BEHIND_THE_GATE.check(
                imported(StoreHandleHoldingFixture.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Field <" + StoreHandleHoldingFixture.class.getName() + ".template>")
                .hasMessageContaining("StringRedisTemplate");
    }

    @Test
    void storeHandlesStayBehindTheGate_WhenAConstructorTakesATemplate_FailsNamingTheParameter() {
        assertThatThrownBy(() -> RedisRoleGateRule.STORE_HANDLES_STAY_BEHIND_THE_GATE.check(
                imported(StoreHandleInjectedFixture.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Parameter 0 of <" + StoreHandleInjectedFixture.class.getName() + ".<init>(")
                .hasMessageContaining("StringRedisTemplate");
    }

    @Test
    void storeHandlesStayBehindTheGate_WhenAClassReadsThroughAGate_Passes() {
        RedisRoleGateRule.STORE_HANDLES_STAY_BEHIND_THE_GATE.check(imported(GatedStoreReadingFixture.class));
    }

    @Test
    void storeHandlesStayBehindTheGate_WhenProductionIsChecked_Passes() {
        RedisRoleGateRule.STORE_HANDLES_STAY_BEHIND_THE_GATE.check(ImportedClasses.production());
    }

    @Test
    void roleHealthReaders_WhenAnEntryIsRemoved_ReportsThatEntry() {
        assertEachEntryIsStillNeeded(RedisRoleGateRule.ROLE_HEALTH_READERS,
                RedisRoleGateRule::onlyTheGateReadsRoleHealth);
    }

    @Test
    void storeDefinitions_WhenAnEntryIsRemoved_ReportsThatEntry() {
        assertEachEntryIsStillNeeded(RedisRoleGateRule.STORE_DEFINITIONS, definitions ->
                RedisRoleGateRule.storeHandlesStayBehindTheGate(definitions, RedisRoleGateRule.STORE_HANDLE_HOLDERS));
    }

    @Test
    void storeHandleHolders_WhenAnEntryIsRemoved_ReportsThatEntry() {
        assertEachEntryIsStillNeeded(RedisRoleGateRule.STORE_HANDLE_HOLDERS, holders ->
                RedisRoleGateRule.storeHandlesStayBehindTheGate(RedisRoleGateRule.STORE_DEFINITIONS, holders));
    }

    private static JavaClasses imported(Class<?> fixture) {
        return new ClassFileImporter().importClasses(fixture);
    }

    private static void assertEachEntryIsStillNeeded(Set<String> listed, Function<Set<String>, ArchRule> rule) {
        assertThat(listed).isNotEmpty().allSatisfy(entry -> {
            Set<String> without = new HashSet<>(listed);
            without.remove(entry);
            List<String> reported = rule.apply(without).evaluate(ImportedClasses.production())
                    .getFailureReport().getDetails();

            assertThat(reported).as("%s no longer needs its exemption; delete it from its list", entry)
                    .anyMatch(detail -> detail.contains("<" + entry + ".") || detail.contains("<" + entry + "$")
                            || detail.contains("<" + entry + ">"));
        });
    }
}
