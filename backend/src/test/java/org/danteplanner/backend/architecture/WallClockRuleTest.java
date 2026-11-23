package org.danteplanner.backend.architecture;

import java.time.Clock;
import java.util.Date;
import java.util.List;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import org.danteplanner.backend.shared.config.TaskSchedulerConfig;
import org.danteplanner.fixture.wallclock.SpringAuditedFixture;
import org.danteplanner.fixture.wallclock.WallClockFixture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WallClockRuleTest {

    static final ArchRule THE_WALL_CLOCK_IS_READ_THROUGH_THE_CLOCK_BEAN =
            noClasses()
                    .should().accessTargetWhere(readsTheWallClock())
                    .as("main code reads the wall clock only through the injected java.time.Clock bean;"
                            + " a state transition takes Instant now from its caller");

    static final ArchRule AUDITED_ENTITIES_USE_THE_CONTEXT_OWNED_LISTENER =
            noClasses()
                    .should().dependOnClassesThat().areAssignableTo(AuditingEntityListener.class)
                    .as("main code never names Spring's AuditingEntityListener, whose handler comes from a JVM-wide"
                            + " aspect; an audited entity names AuditedTimestampListener");

    @Test
    void production_WhenScanned_NeverNamesSpringsAuditingListener() {
        assertThatCode(() -> AUDITED_ENTITIES_USE_THE_CONTEXT_OWNED_LISTENER.check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void fixture_WhenItNamesSpringsAuditingListener_FailsNamingIt() {
        assertThatThrownBy(() -> AUDITED_ENTITIES_USE_THE_CONTEXT_OWNED_LISTENER.check(
                new ClassFileImporter().importClasses(SpringAuditedFixture.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(SpringAuditedFixture.class.getName());
    }

    @Test
    void production_WhenScanned_ReadsTheWallClockOnlyThroughTheBean() {
        assertThatCode(() -> THE_WALL_CLOCK_IS_READ_THROUGH_THE_CLOCK_BEAN.check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void fixture_WhenItReadsTheWallClock_FailsNamingEachReader() {
        String reader = "Method <" + WallClockFixture.class.getName() + ".";

        assertThatThrownBy(() -> THE_WALL_CLOCK_IS_READ_THROUGH_THE_CLOCK_BEAN.check(
                new ClassFileImporter().importClasses(WallClockFixture.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(reader + "instantNow()")
                .hasMessageContaining(reader + "localDateNow()")
                .hasMessageContaining(reader + "localDateTimeNow()")
                .hasMessageContaining(reader + "newDate()")
                .hasMessageContaining(reader + "millis()")
                .hasMessageContaining(reader + "systemClock()")
                .hasMessageContaining(reader + "referencedNow()")
                .hasMessageContaining(reader + "referencedDate()")
                .hasMessageNotContaining(reader + "elapsedTicks()")
                .hasMessageNotContaining(reader + "injectedNow()")
                .hasMessageNotContaining(reader + "dateOfInstant()");
    }

    private static DescribedPredicate<JavaAccess<?>> readsTheWallClock() {
        return DescribedPredicate.describe("a wall-clock read outside the Clock bean", access ->
                access.getTarget() instanceof CodeUnitAccessTarget target
                        && isWallClockRead(target)
                        && !isTheClockBean(access.getOrigin()));
    }

    private static boolean isWallClockRead(CodeUnitAccessTarget target) {
        JavaClass owner = target.getOwner();
        List<JavaClass> parameters = target.getRawParameterTypes();
        return switch (target.getName()) {
            case "now" -> owner.getPackageName().startsWith("java.time")
                    && parameters.stream().noneMatch(parameter -> parameter.isEquivalentTo(Clock.class));
            case "system", "systemUTC", "systemDefaultZone", "tickMillis", "tickSeconds", "tickMinutes" ->
                    owner.isEquivalentTo(Clock.class);
            case "currentTimeMillis" -> owner.isEquivalentTo(System.class);
            case "<init>" -> owner.isEquivalentTo(Date.class) && parameters.isEmpty();
            default -> false;
        };
    }

    private static boolean isTheClockBean(JavaCodeUnit origin) {
        return origin.getOwner().isEquivalentTo(TaskSchedulerConfig.class) && origin.getName().equals("clock");
    }
}
