package org.danteplanner.fixture.failureunion;

import com.google.errorprone.annotations.CheckReturnValue;

import org.springframework.transaction.annotation.Transactional;

import org.danteplanner.backend.shared.failure.Result;

public final class ResultReturners {

    private ResultReturners() {
    }

    public static final class Unannotated {

        public Result<String, String> discardable() {
            return Result.ok("");
        }
    }

    public static final class AnnotatedItself {

        @CheckReturnValue
        public Result<String, String> kept() {
            return Result.ok("");
        }
    }

    @CheckReturnValue
    public static final class AnnotatedAround {

        public static final class Inner {

            public Result<String, String> keptByTheEnclosingClass() {
                return Result.ok("");
            }
        }
    }

    @CheckReturnValue
    public static class TransactionalReturner {

        @Transactional
        public Result<String, String> settle() {
            return Result.err("");
        }
    }
}
