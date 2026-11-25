package org.danteplanner.backend.architecture;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.failure.Result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultTest {

    private static final Result<Integer, String> OK = Result.ok(2);
    private static final Result<Integer, String> ERR = Result.err("refused");

    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void map_WhenOk_AppliesTheFunction() {
        assertThat(OK.map(value -> "v" + value)).isEqualTo(Result.ok("v2"));
    }

    @Test
    void map_WhenErr_KeepsTheRefusalAndSkipsTheFunction() {
        assertThat(ERR.map(value -> calls.incrementAndGet())).isEqualTo(Result.err("refused"));
        assertThat(calls).hasValue(0);
    }

    @Test
    void flatMap_WhenOk_ReturnsTheFunctionsResult() {
        assertThat(OK.flatMap(value -> Result.ok("v" + value))).isEqualTo(Result.ok("v2"));
        assertThat(OK.flatMap(value -> Result.err("no " + value))).isEqualTo(Result.err("no 2"));
    }

    @Test
    void flatMap_WhenErr_KeepsTheRefusalAndSkipsTheFunction() {
        Result<Integer, String> chained = ERR.flatMap(value -> Result.ok(calls.incrementAndGet()));

        assertThat(chained).isEqualTo(Result.err("refused"));
        assertThat(calls).hasValue(0);
    }

    @Test
    void orElseThrow_WhenOk_ReturnsTheValue() {
        assertThat(OK.orElseThrow(IllegalStateException::new)).isEqualTo(2);
    }

    @Test
    void orElseThrow_WhenErr_ThrowsTheExceptionBuiltFromTheRefusal() {
        assertThatThrownBy(() -> ERR.orElseThrow(IllegalStateException::new))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("refused");
    }
}
