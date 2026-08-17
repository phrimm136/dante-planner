package org.danteplanner.backend.auth.token;

import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.exception.SessionRevokedException;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What every rotation outcome means, as one table.
 *
 * <p>{@link RotationResult#orThrow()} is the single reading of a non-rotation, and both the filter
 * chain and the session service act on the exception it picks. A theft that surfaced as anything
 * other than {@link SessionRevokedException} would not clear the client's cookies, leaving the
 * stolen session live, so the mapping is pinned per outcome rather than sampled.</p>
 *
 * <p>The axis is derived from the sealed hierarchy's permitted subclasses: a fourth outcome added
 * to {@link RotationResult} fails here until this table says what it means.</p>
 */
class RotationResultMatrixTest {

    private static final String FAMILY_ID = "fam-1";

    /**
     * Every outcome, and the failure a caller is owed for it. {@code null} marks the one outcome
     * that is not a failure.
     */
    private static Map<RotationResult, Class<? extends RuntimeException>> outcomes() {
        Map<RotationResult, Class<? extends RuntimeException>> table = new LinkedHashMap<>();
        table.put(new RotationResult.Rotated("new.jwt", null), null);
        table.put(new RotationResult.Revoked(FAMILY_ID), SessionRevokedException.class);
        table.put(new RotationResult.Rejected(RotationResult.Rejected.Reason.REVOKED_FAMILY),
                SessionRevokedException.class);
        table.put(new RotationResult.Rejected(RotationResult.Rejected.Reason.INVALID),
                InvalidTokenException.class);
        return table;
    }

    @TestFactory
    Stream<DynamicTest> orThrow_WhenOutcomeIsGiven_RaisesTheFailureTheCallerIsOwed() {
        return outcomes().entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey().getClass().getSimpleName() + " -> "
                        + (entry.getValue() == null ? "no failure" : entry.getValue().getSimpleName()),
                () -> {
                    RotationResult outcome = entry.getKey();
                    if (entry.getValue() == null) {
                        assertSame(outcome, outcome.orThrow(),
                                "a successful rotation is returned, not wrapped");
                    } else {
                        assertThrows(entry.getValue(), outcome::orThrow);
                    }
                }));
    }

    @TestFactory
    Stream<DynamicTest> orThrow_WhenOutcomeIsAdded_HasARowInThisTable() {
        return RotationResult.class.getPermittedSubclasses() == null
                ? Stream.empty()
                : Stream.of(RotationResult.class.getPermittedSubclasses())
                        .map(permitted -> DynamicTest.dynamicTest(
                                permitted.getSimpleName() + " is covered",
                                () -> assertEquals(1,
                                        outcomes().keySet().stream()
                                                .filter(permitted::isInstance)
                                                .findAny().stream().count(),
                                        permitted.getSimpleName()
                                                + " has no row; say what a caller is owed for it")));
    }

    @TestFactory
    Stream<DynamicTest> orThrow_WhenFamilyIsRevoked_CarriesTheFamilyForward() {
        return Stream.of(DynamicTest.dynamicTest("Revoked carries its family id", () -> {
            SessionRevokedException thrown = assertThrows(SessionRevokedException.class,
                    () -> new RotationResult.Revoked(FAMILY_ID).orThrow());
            // The family is what a responder revokes; losing it here would revoke nothing.
            assertEquals(FAMILY_ID, thrown.getFamilyId());
        }));
    }
}
