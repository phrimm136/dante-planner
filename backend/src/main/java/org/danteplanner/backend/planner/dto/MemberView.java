package org.danteplanner.backend.planner.dto;

import org.danteplanner.backend.planner.entity.Upvote;
import org.danteplanner.backend.shared.dto.NotificationSetting;

public record MemberView(Upvote upvote, Subscription subscription, ReportState report,
        NotificationSetting ownerNotifications) {

    public static final MemberView GUEST = new MemberView(Upvote.NONE, Subscription.NOT_SUBSCRIBED,
            ReportState.NOT_REPORTED, NotificationSetting.OFF);
}
