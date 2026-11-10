package org.danteplanner.backend.integration.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyDrawTest {

    private static final List<String> POOL = List.of("p0", "p1", "p2", "p3");

    @Test
    void share_WhenDrawnFromAPool_PicksTheShareOfItsSize() {
        assertThat(KeyDraw.share(0).from(POOL)).isEqualTo("p0");
        assertThat(KeyDraw.share(0.5).from(POOL)).isEqualTo("p2");
        assertThat(KeyDraw.share(0.99).from(POOL)).isEqualTo("p3");
    }

    @Test
    void share_WhenOutsideZeroToOne_IsRejected() {
        assertThatThrownBy(() -> KeyDraw.share(1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not 1.0");
        assertThatThrownBy(() -> KeyDraw.share(-0.1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not -0.1");
    }

    @Test
    void share_WhenThePoolIsEmpty_Throws() {
        assertThatThrownBy(() -> KeyDraw.share(0).from(List.of())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty planner pool");
    }

    @Test
    void index_WhenDrawnFromAPool_PicksThatPosition() {
        assertThat(KeyDraw.index(0).from(POOL)).isEqualTo("p0");
        assertThat(KeyDraw.index(1).from(POOL)).isEqualTo("p1");
    }

    @Test
    void index_WhenNegative_IsRejected() {
        assertThatThrownBy(() -> KeyDraw.index(-1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not -1");
    }

    @Test
    void index_WhenBeyondThePool_Throws() {
        assertThatThrownBy(() -> KeyDraw.index(4).from(POOL)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pool index 4 is outside a pool of 4");
    }
}
