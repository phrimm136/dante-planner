package org.danteplanner.backend.support;

import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.RedisConnectionFailureException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.danteplanner.backend.support.RecordingSessionEndHandler.EndAllSessions;
import org.danteplanner.backend.support.RecordingSessionEndHandler.Failed;
import org.danteplanner.backend.support.RecordingSessionEndHandler.Operation;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@ExtendWith(ScriptedFailuresCheck.class)
class RecordingSessionEndHandlerTest {

    private static final long USER_ID = 7L;
    private static final int GRACE_PERIOD_DAYS = 30;

    @Mock
    private UserRepository userRepository;

    private final RecordingSessionEndHandler sessions = new RecordingSessionEndHandler();

    @Test
    void calls_WhenTheRealLifecycleDeletesAnAccount_RecordThatAllItsSessionsEnd() {
        when(userRepository.findWithLockById(USER_ID)).thenReturn(Optional.of(TestDataFactory.unsavedUser(USER_ID)));

        new UserAccountLifecycleService(userRepository, List.of(), sessions, GRACE_PERIOD_DAYS).deleteAccount(USER_ID);

        assertThat(sessions.calls()).containsExactly(new EndAllSessions(USER_ID));
    }

    @Test
    void failNext_WhenScripted_FailsOneCallAndThenRecovers() {
        when(userRepository.findWithLockById(USER_ID)).thenReturn(Optional.of(TestDataFactory.unsavedUser(USER_ID)));
        RedisConnectionFailureException down = new RedisConnectionFailureException("down");
        sessions.failNext(Operation.END_ALL_SESSIONS, down);
        UserAccountLifecycleService lifecycle =
                new UserAccountLifecycleService(userRepository, List.of(), sessions, GRACE_PERIOD_DAYS);

        assertThatThrownBy(() -> lifecycle.deleteAccount(USER_ID))
                .isInstanceOf(RedisConnectionFailureException.class)
                .hasMessage("down");
        sessions.endAllSessions(USER_ID);

        assertThat(sessions.calls())
                .containsExactly(new Failed(Operation.END_ALL_SESSIONS, down), new EndAllSessions(USER_ID));
    }
}
