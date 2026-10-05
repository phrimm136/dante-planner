package org.danteplanner.backend.architecture;

import org.danteplanner.backend.support.LogCapture;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import ch.qos.logback.core.spi.AppenderAttachable;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

final class LogCaptureRule {

    static final ArchRule APPENDERS_ATTACH_ONLY_THROUGH_LOG_CAPTURE =
            classes()
                    .should(attachNoAppenderOutsideLogCapture())
                    .as("only LogCapture attaches a logback appender; a test captures its own thread's"
                            + " log lines through it");

    private LogCaptureRule() {
    }

    private static ArchCondition<JavaClass> attachNoAppenderOutsideLogCapture() {
        return new ArchCondition<>("attach no logback appender outside LogCapture") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                if (isLogCapture(clazz)) {
                    return;
                }
                clazz.getAccessesFromSelf().stream()
                        .filter(LogCaptureRule::attachesAnAppender)
                        .forEach(access -> events.add(SimpleConditionEvent.violated(
                                access,
                                access.getOrigin().getFullName() + " attaches a logback appender outside LogCapture")));
            }
        };
    }

    private static boolean isLogCapture(JavaClass clazz) {
        String capture = LogCapture.class.getName();
        return clazz.getName().equals(capture) || clazz.getName().startsWith(capture + "$");
    }

    private static boolean attachesAnAppender(JavaAccess<?> access) {
        return access instanceof JavaCodeUnitAccess<?>
                && access.getName().equals("addAppender")
                && access.getTargetOwner().isAssignableTo(AppenderAttachable.class);
    }
}
