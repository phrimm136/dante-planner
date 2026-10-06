package org.danteplanner.backend.support;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.danteplanner.backend.support.RecordingAccountLifecycleParticipant.Call;
import org.danteplanner.backend.support.RecordingAccountLifecycleParticipant.Event;
import org.danteplanner.backend.support.RecordingAccountLifecycleParticipant.Failed;
import org.danteplanner.backend.support.RecordingSessionEndHandler.EndAllSessions;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@ExtendWith(ScriptedFailuresCheck.class)
class RecordingAccountLifecycleParticipantTest {

    private static final long USER_ID = 7L;
    private static final int GRACE_PERIOD_DAYS = 30;

    @Mock
    private UserRepository userRepository;

    private final List<Object> journal = new ArrayList<>();
    private final RecordingAccountLifecycleParticipant first = new RecordingAccountLifecycleParticipant(journal);
    private final RecordingAccountLifecycleParticipant second = new RecordingAccountLifecycleParticipant(journal);

    @Test
    void calls_WhenTheRealLifecycleDeletesAnAccount_RecordEachParticipantBeforeTheSessionsEnd() {
        when(userRepository.findWithLockById(USER_ID)).thenReturn(Optional.of(TestDataFactory.unsavedUser(USER_ID)));

        lifecycle().deleteAccount(USER_ID);

        assertThat(journal).containsExactly(
                new Call(first, Event.DEACTIVATED, USER_ID, null),
                new Call(second, Event.DEACTIVATED, USER_ID, null),
                new EndAllSessions(USER_ID));
        assertThat(first.calls()).containsExactly(new Call(first, Event.DEACTIVATED, USER_ID, null));
    }

    @Test
    void calls_WhenTheRealLifecycleReactivatesADeletedAccount_RecordTheReactivation() {
        User user = TestDataFactory.unsavedUser(USER_ID);
        user.softDelete(Instant.now().plus(Duration.ofDays(GRACE_PERIOD_DAYS)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        lifecycle().reactivateAccount(USER_ID);

        assertThat(first.calls()).containsExactly(new Call(first, Event.REACTIVATED, USER_ID, null));
        assertThat(second.calls()).containsExactly(new Call(second, Event.REACTIVATED, USER_ID, null));
    }

    @Test
    void failNext_WhenScripted_FailsOneCallAndThenRecovers() {
        User user = TestDataFactory.unsavedUser(USER_ID);
        user.softDelete(Instant.now().plus(Duration.ofDays(GRACE_PERIOD_DAYS)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        IllegalStateException down = new IllegalStateException("participant down");
        second.failNext(Event.REACTIVATED, down);

        assertThatThrownBy(() -> lifecycle().reactivateAccount(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("participant down");
        second.accountReactivated(USER_ID);

        assertThat(journal).containsExactly(
                new Call(first, Event.REACTIVATED, USER_ID, null),
                new Failed(second, Event.REACTIVATED, USER_ID, down),
                new Call(second, Event.REACTIVATED, USER_ID, null));
        assertThat(second.failed()).containsExactly(new Failed(second, Event.REACTIVATED, USER_ID, down));
    }

    private UserAccountLifecycleService lifecycle() {
        return new UserAccountLifecycleService(userRepository, List.of(first, second),
                new RecordingSessionEndHandler(journal), GRACE_PERIOD_DAYS);
    }
}
