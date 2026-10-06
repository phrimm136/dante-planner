package org.danteplanner.backend.support;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class ScriptedFailuresCheck implements BeforeEachCallback, AfterEachCallback {

    private static final ThreadLocal<List<ScriptedFailures<?>>> SCRIPTED = new ThreadLocal<>();

    static void register(ScriptedFailures<?> failures) {
        List<ScriptedFailures<?>> scripted = SCRIPTED.get();
        if (scripted == null) {
            throw new IllegalStateException("failNext needs the test class to be extended with "
                    + ScriptedFailuresCheck.class.getSimpleName());
        }
        if (scripted.stream().noneMatch(registered -> registered == failures)) {
            scripted.add(failures);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        SCRIPTED.set(new ArrayList<>());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        List<ScriptedFailures<?>> scripted = SCRIPTED.get();
        SCRIPTED.remove();
        List<String> unconsumed = scripted == null ? List.of() : scripted.stream()
                .flatMap(failures -> failures.unconsumed().stream())
                .toList();
        if (!unconsumed.isEmpty()) {
            throw new AssertionError("scripted failures the subject never triggered: " + unconsumed);
        }
    }
}
