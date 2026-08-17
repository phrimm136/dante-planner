package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Freezes the SSE fan-out seam: every dispatch reaches connected clients through Redis.
 *
 * <p>The methods below deliver to emitters held by <em>this</em> pod only. They are the tail of a
 * fan-out — what the Redis subscriber calls once an event arrives — so a caller outside the SSE
 * package reaches only the clients that happen to share its pod, and the event is silently lost for
 * everyone else. Call sites publish instead, and the subscriber dispatches on every pod.</p>
 *
 * <p>The inverse is equally load-bearing and is why publishing lives at the call site rather than
 * inside these methods: a dispatch method that published would re-publish whatever the subscriber
 * handed it, looping between pods forever.</p>
 */
@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class SseDispatchBoundaryTest {

    @ArchTest
    static final ArchRule sse_dispatch_is_only_reached_through_the_fan_out =
            methods()
                    .that().areDeclaredInClassesThat().resideInAPackage("..shared.sse..")
                    .and().haveNameMatching(
                            "sendToUser|broadcastToAll|notifyAccountSuspended|invalidateSettingsCache")
                    .should().onlyBeCalled().byClassesThat().resideInAPackage("..shared.sse..")
                    .as("SSE dispatch delivers to this pod only; call sites must publish so every pod dispatches");
}
