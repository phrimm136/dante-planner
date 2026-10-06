package org.danteplanner.backend.support;

import java.util.HashMap;
import java.util.Map;

import org.danteplanner.backend.shared.sse.NotificationPreferenceProvider;
import org.danteplanner.backend.user.api.UserNotFoundException;

public class FakeNotificationPreferences implements NotificationPreferenceProvider {

    private static final Preferences DEFAULTS = new Preferences(true, true, false);

    private final FakeUsers users;
    private final Map<Long, Preferences> preferences = new HashMap<>();

    public FakeNotificationPreferences(FakeUsers users) {
        this.users = users;
    }

    public void set(Long userId, Preferences userPreferences) {
        requireUser(userId);
        preferences.put(userId, userPreferences);
    }

    @Override
    public Preferences preferencesOf(Long userId) {
        Preferences stored = preferences.get(userId);
        if (stored != null) {
            return stored;
        }
        requireUser(userId);
        preferences.put(userId, DEFAULTS);
        return DEFAULTS;
    }

    private void requireUser(Long userId) {
        if (users.findOptionalById(userId).isEmpty()) {
            throw new UserNotFoundException(userId);
        }
    }
}
