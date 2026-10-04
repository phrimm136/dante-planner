package org.danteplanner.backend.shared.sse;

public interface NotificationPreferenceProvider {

    Preferences preferencesOf(Long userId);

    record Preferences(
            boolean notifyComments,
            boolean notifyRecommendations,
            boolean notifyNewPublications
    ) {
    }
}
