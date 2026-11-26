package org.danteplanner.fixture.decisionpurity;

import java.security.Guard;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import lombok.Builder;
import lombok.Setter;

import org.danteplanner.backend.shared.failure.Parsed;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

final class ParseConventionFixtures {

    private ParseConventionFixtures() {
    }

    @Parsed
    record Email(String value) {

        static Email parse(String raw) {
            return new Email(raw);
        }

        static List<Email> parseAll(List<String> raw) {
            return raw.stream().map(value -> new Email(value)).toList();
        }

        static Function<String, Email> parser() {
            return Email::new;
        }

        static Supplier<Email> anonymously(String raw) {
            return new Supplier<>() {
                @Override
                public Email get() {
                    return new Email(raw);
                }
            };
        }

        static final class NestedBuilder {

            Email nestedConstruction(String raw) {
                return new Email(raw);
            }
        }
    }

    @Parsed
    enum Grade {

        PLAIN,
        WITH_BODY {
            @Override
            int weight() {
                return 1;
            }
        };

        int weight() {
            return 0;
        }
    }

    @Parsed
    static class Open {
    }

    @Parsed
    interface SealedFact {
    }

    static final class Subclass extends Open {
    }

    static final class Elsewhere {

        Email directConstruction(String raw) {
            return new Email(raw);
        }

        Function<String, Email> referencedConstruction() {
            return Email::new;
        }

        Supplier<Email> lambdaConstruction(String raw) {
            return () -> new Email(raw);
        }

        Open anonymousSubclass() {
            return new Open() {
            };
        }

        Email throughTheFactory(String raw) {
            return Email.parse(raw);
        }
    }

    interface Checks {

        void requireDeclared(String value);
    }

    static class VoidChecks implements Checks {

        @Override
        public void requireDeclared(String value) {
        }

        void requirePresent(String value) {
        }

        private static void checkRange(int value) {
        }

        protected void validateName(String value) {
        }

        void ensureOpen() {
        }

        void verifyOwner() {
        }

        Runnable requireInALambda() {
            return () -> {
            };
        }

        String requireReturned(String value) {
            return value;
        }

        void record(String value) {
        }
    }

    static final class LibraryGuard implements Guard {

        @Override
        public void checkGuard(Object object) {
        }
    }

    interface StoredFlags {

        @Modifying
        @Query("UPDATE Row r SET r.enabled = :enabled WHERE r.id = :id")
        int assignEnabled(long id, boolean enabled);

        @Modifying
        @Query("UPDATE Row r SET r.count = r.count + 1 WHERE r.id = :id AND r.hidden = :hidden")
        int incrementVisible(long id, boolean hidden);

        @Modifying
        @Query("UPDATE Row r SET r.state = CASE WHEN :chosen = TRUE THEN 'A' ELSE 'B' END WHERE r.id = :id")
        int assignChosen(long id, boolean chosen);
    }

    interface Flagged {

        void declaredFlag(boolean flag);
    }

    static class HiddenFlag {

        protected void widenedFlag(boolean flag) {
        }
    }

    static class PublicFlags extends HiddenFlag implements Flagged {

        public PublicFlags(boolean flag) {
        }

        @Override
        public void declaredFlag(boolean flag) {
        }

        @Override
        public void widenedFlag(boolean flag) {
        }

        public void primitiveFlag(String value, boolean flag) {
        }

        public static void boxedFlag(Boolean flag) {
        }

        public void setTwo(boolean flag, String value) {
        }

        public void settle(boolean flag) {
        }

        public void setEnabled(boolean enabled) {
        }

        public void unflagged(String value) {
        }

        void packagePrivateFlag(boolean flag) {
        }

        private void privateFlag(boolean flag) {
        }
    }

    record FlagRecord(boolean enabled) {
    }

    @RestController
    static final class FlagController {

        public Grade boundRequestFlag(@RequestParam(defaultValue = "false") boolean weighted) {
            return weighted ? Grade.WITH_BODY : Grade.PLAIN;
        }

        public void unboundFlag(boolean flag) {
        }
    }

    static final class LibraryFuture extends CompletableFuture<String> {

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }
    }

    static final class LibraryConsumer implements BiConsumer<String, Boolean> {

        @Override
        public void accept(String value, Boolean flag) {
        }

        public void accept(Integer value, Boolean flag) {
        }
    }

    @Builder
    @Setter
    static final class LombokFlags {

        private boolean generated;
    }
}
