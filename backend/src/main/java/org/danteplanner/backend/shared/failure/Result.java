package org.danteplanner.backend.shared.failure;

import com.google.errorprone.annotations.CheckReturnValue;

import java.util.function.Function;

@CheckReturnValue
public sealed interface Result<T, E> extends FailureUnion {

    record Ok<T, E>(T value) implements Result<T, E> {
    }

    record Err<T, E>(E refusal) implements Result<T, E> {
    }

    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    static <T, E> Result<T, E> err(E refusal) {
        return new Err<>(refusal);
    }

    default <U> Result<U, E> map(Function<? super T, ? extends U> f) {
        return flatMap(value -> ok(f.apply(value)));
    }

    default <U> Result<U, E> flatMap(Function<? super T, Result<U, E>> f) {
        return switch (this) {
            case Ok<T, E>(T value) -> f.apply(value);
            case Err<T, E>(E refusal) -> err(refusal);
        };
    }

    default <X extends RuntimeException> T orElseThrow(Function<? super E, X> toException) {
        return switch (this) {
            case Ok<T, E>(T value) -> value;
            case Err<T, E>(E refusal) -> throw toException.apply(refusal);
        };
    }
}
