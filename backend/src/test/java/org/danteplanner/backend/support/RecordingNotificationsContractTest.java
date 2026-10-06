package org.danteplanner.backend.support;

import org.danteplanner.backend.notification.api.Notifications;
import org.danteplanner.backend.user.entity.User;

class RecordingNotificationsContractTest implements NotificationsContract {

    private final FakeUsers users = new FakeUsers();
    private final RecordingNotifications notifications = new RecordingNotifications(users);

    @Override
    public Notifications notifications() {
        return notifications;
    }

    @Override
    public User persisted(User user) {
        return users.add(user);
    }
}
