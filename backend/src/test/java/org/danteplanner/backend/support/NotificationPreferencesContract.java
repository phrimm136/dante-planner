package org.danteplanner.backend.support;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.sse.NotificationPreferenceProvider;
import org.danteplanner.backend.shared.sse.NotificationPreferenceProvider.Preferences;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public interface NotificationPreferencesContract {

    long UNKNOWN_USER_ID = Long.MAX_VALUE;

    NotificationPreferenceProvider preferences();

    User persisted(User user);

    void set(Long userId, Preferences userPreferences);

    @Test
    default void preferencesOf_WhenTheUserHasNoSettings_AnswersTheDefaults() {
        User user = persisted(UsersContract.newUser().build());

        assertThat(preferences().preferencesOf(user.getId())).isEqualTo(new Preferences(true, true, false));
    }

    @Test
    default void preferencesOf_WhenTheUserSetThem_AnswersTheSetValues() {
        User user = persisted(UsersContract.newUser().build());
        Preferences chosen = new Preferences(false, true, true);

        set(user.getId(), chosen);

        assertThat(preferences().preferencesOf(user.getId())).isEqualTo(chosen);
    }

    @Test
    default void preferencesOf_WhenTheUserDoesNotExist_ThrowsUserNotFound() {
        assertThatThrownBy(() -> preferences().preferencesOf(UNKNOWN_USER_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found with id: " + UNKNOWN_USER_ID);
    }
}
