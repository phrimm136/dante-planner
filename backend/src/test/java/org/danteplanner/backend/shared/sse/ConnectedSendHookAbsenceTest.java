package org.danteplanner.backend.shared.sse;

import org.danteplanner.backend.comment.service.PlannerCommentSseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The initial {@code connected} event is buffered until Spring attaches the emitter's handler, so
 * its send cannot fail at registration time. Delivery failure belongs to {@code onError}.
 */
class ConnectedSendHookAbsenceTest {

    private static final List<Class<?>> SSE_SERVICES =
            List.of(AbstractSseService.class, SseService.class, PlannerCommentSseService.class);

    @Test
    @DisplayName("No SSE service declares a connected-send failure hook")
    void connectedSendFailureHook_WhenSearchedForAcrossTheHierarchy_IsDeclaredNowhere() {
        assertThat(SSE_SERVICES)
                .allSatisfy(type -> assertThat(Arrays.stream(type.getDeclaredMethods()).map(Method::getName))
                        .doesNotContain("onConnectedSendFailure"));
    }
}
