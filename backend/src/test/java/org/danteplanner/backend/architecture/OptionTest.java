package org.danteplanner.backend.architecture;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.failure.Option;
import org.danteplanner.backend.shared.failure.Result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionTest {

    private static final Option<Integer> SOME = new Option.Some<>(2);
    private static final Option<Integer> NONE = new Option.None<>();

    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void some_WhenGivenNull_IsRefused() {
        assertThatThrownBy(() -> new Option.Some<>(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value");
    }

    @Test
    void fromNullable_WhenNullOrPresent_ReturnsNoneOrSome() {
        assertThat(Option.fromNullable(null)).isEqualTo(new Option.None<>());
        assertThat(Option.fromNullable("x")).isEqualTo(new Option.Some<>("x"));
    }

    @Test
    void from_WhenTheOptionalIsEmptyOrPresent_ReturnsNoneOrSome() {
        assertThat(Option.from(Optional.empty())).isEqualTo(new Option.None<>());
        assertThat(Option.from(Optional.of("x"))).isEqualTo(new Option.Some<>("x"));
    }

    @Test
    void map_WhenSome_AppliesTheFunction() {
        assertThat(SOME.map(value -> "v" + value)).isEqualTo(new Option.Some<>("v2"));
    }

    @Test
    void map_WhenNone_SkipsTheFunction() {
        assertThat(NONE.map(value -> calls.incrementAndGet())).isEqualTo(new Option.None<>());
        assertThat(calls).hasValue(0);
    }

    @Test
    void map_WhenTheFunctionReturnsNull_IsRefused() {
        assertThatThrownBy(() -> SOME.map(value -> null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value");
    }

    @Test
    void flatMap_WhenSome_ReturnsTheFunctionsOption() {
        assertThat(SOME.flatMap(value -> new Option.Some<>("v" + value))).isEqualTo(new Option.Some<>("v2"));
        assertThat(SOME.flatMap(value -> new Option.None<String>())).isEqualTo(new Option.None<>());
    }

    @Test
    void flatMap_WhenNone_SkipsTheFunction() {
        assertThat(NONE.flatMap(value -> new Option.Some<>(calls.incrementAndGet()))).isEqualTo(new Option.None<>());
        assertThat(calls).hasValue(0);
    }

    @Test
    void filter_WhenSomeKeptOrRejected_ReturnsSomeOrNone() {
        assertThat(SOME.filter(value -> value == 2)).isEqualTo(new Option.Some<>(2));
        assertThat(SOME.filter(value -> value != 2)).isEqualTo(new Option.None<>());
    }

    @Test
    void filter_WhenNone_SkipsThePredicate() {
        assertThat(NONE.filter(value -> calls.incrementAndGet() > 0)).isEqualTo(new Option.None<>());
        assertThat(calls).hasValue(0);
    }

    @Test
    void stream_WhenSomeOrNone_HoldsTheValueOrNothing() {
        assertThat(SOME.stream()).containsExactly(2);
        assertThat(NONE.stream()).isEmpty();
    }

    @Test
    void fold_WhenSomeOrNone_TakesTheMatchingBranch() {
        assertThat(SOME.fold(value -> "some " + value, () -> "none")).isEqualTo("some 2");
        assertThat(NONE.fold(value -> "some " + value, () -> "none")).isEqualTo("none");
    }

    @Test
    void ifSome_WhenSomeOrNone_RunsTheActionOnlyForSome() {
        List<Integer> seen = new ArrayList<>();

        SOME.ifSome(seen::add);
        NONE.ifSome(seen::add);

        assertThat(seen).containsExactly(2);
    }

    @Test
    void orElseThrow_WhenSome_ReturnsTheValueWithoutBuildingTheRefusal() {
        assertThat(SOME.orElseThrow(() -> new IllegalStateException("refused " + calls.incrementAndGet())))
                .isEqualTo(2);
        assertThat(calls).hasValue(0);
    }

    @Test
    void orElseThrow_WhenNone_ThrowsTheSuppliedRefusal() {
        assertThatThrownBy(() -> NONE.orElseThrow(() -> new IllegalStateException("refused")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("refused");
    }

    @Test
    void okOr_WhenSomeOrNone_ReturnsOkOrTheSuppliedRefusal() {
        assertThat(SOME.okOr(() -> "missing")).isEqualTo(Result.ok(2));
        assertThat(NONE.okOr(() -> "missing")).isEqualTo(Result.err("missing"));
    }
}
