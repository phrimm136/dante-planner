package org.danteplanner.backend.shared.failure;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.errorprone.annotations.CheckReturnValue;
import org.jspecify.annotations.Nullable;

@ReadResult
public sealed interface Option<T> {

    record Some<T>(T value) implements Option<T> {

        public Some {
            Objects.requireNonNull(value, "value");
        }
    }

    record None<T>() implements Option<T> {
    }

    static <T> Option<T> fromNullable(@Nullable T value) {
        return value == null ? new None<>() : new Some<>(value);
    }

    static <T> Option<T> from(Optional<T> value) {
        return value.<Option<T>>map(Some::new).orElseGet(None::new);
    }

    default <U> Option<U> map(Function<? super T, ? extends U> mapper) {
        return flatMap(value -> new Some<>(mapper.apply(value)));
    }

    default <U> Option<U> flatMap(Function<? super T, Option<U>> mapper) {
        return switch (this) {
            case Some<T>(T value) -> mapper.apply(value);
            case None<T>() -> new None<>();
        };
    }

    default Option<T> filter(Predicate<? super T> kept) {
        return flatMap(value -> kept.test(value) ? new Some<>(value) : new None<>());
    }

    default Stream<T> stream() {
        return fold(Stream::of, Stream::empty);
    }

    default <R> R fold(Function<? super T, ? extends R> some, Supplier<? extends R> none) {
        return switch (this) {
            case Some<T>(T value) -> some.apply(value);
            case None<T>() -> none.get();
        };
    }

    default void ifSome(Consumer<? super T> action) {
        if (this instanceof Some<T>(T value)) {
            action.accept(value);
        }
    }

    default <X extends Throwable> T orElseThrow(Supplier<? extends X> refusal) throws X {
        return switch (this) {
            case Some<T>(T value) -> value;
            case None<T>() -> throw refusal.get();
        };
    }

    @CheckReturnValue
    default <E> Result<T, E> okOr(Supplier<? extends E> refusal) {
        return fold(Result::ok, () -> Result.err(refusal.get()));
    }
}
