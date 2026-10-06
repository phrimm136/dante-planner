package org.danteplanner.backend.support;

import org.springframework.data.redis.RedisConnectionFailureException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.support.RecordingSessionEndHandler.Operation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScriptedFailuresCheckTest {

    private static final long USER_ID = 7L;

    private final ScriptedFailuresCheck check = new ScriptedFailuresCheck();

    @AfterEach
    void tearDown() {
        check.afterEach(null);
    }

    @Test
    void afterEach_WhenAScriptIsNeverTriggered_FailsNamingTheFakeAndTheOperation() {
        check.beforeEach(null);
        new RecordingSessionEndHandler().failNext(Operation.END_ALL_SESSIONS, new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> check.afterEach(null))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("scripted failures the subject never triggered")
                .hasMessageContaining("RecordingSessionEndHandler END_ALL_SESSIONS");
    }

    @Test
    void afterEach_WhenEveryScriptIsTriggered_Passes() {
        check.beforeEach(null);
        RecordingSessionEndHandler sessions = new RecordingSessionEndHandler();
        sessions.failNext(Operation.END_ALL_SESSIONS, new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> sessions.endAllSessions(USER_ID))
                .isInstanceOf(RedisConnectionFailureException.class)
                .hasMessage("down");
        assertThatCode(() -> check.afterEach(null)).doesNotThrowAnyException();
    }

    @Test
    void failNext_WhenTheCheckIsNotActive_IsRejected() {
        RecordingSessionEndHandler sessions = new RecordingSessionEndHandler();
        RedisConnectionFailureException down = new RedisConnectionFailureException("down");

        assertThatThrownBy(() -> sessions.failNext(Operation.END_ALL_SESSIONS, down))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("failNext needs the test class to be extended with ScriptedFailuresCheck");
    }
}
