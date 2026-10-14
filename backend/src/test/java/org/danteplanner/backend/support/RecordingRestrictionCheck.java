package org.danteplanner.backend.support;

import java.util.ArrayList;
import java.util.List;

import org.danteplanner.backend.moderation.api.RestrictionCheck;
import org.danteplanner.backend.moderation.service.RestrictionCheckService;
import org.danteplanner.backend.moderation.validation.AccountStandingValidator;
import org.danteplanner.backend.user.api.Users;

public class RecordingRestrictionCheck implements RestrictionCheck {

    public enum Decision {
        CHECKED,
        PRIVATE
    }

    private final RestrictionCheck check;
    private final List<Decision> decisions = new ArrayList<>();

    public RecordingRestrictionCheck(Users users) {
        this.check = new RestrictionCheckService(users, new AccountStandingValidator());
    }

    public List<Decision> decisions() {
        return List.copyOf(decisions);
    }

    @Override
    public void requireUnrestricted(Long userId) {
        decisions.add(Decision.CHECKED);
        check.requireUnrestricted(userId);
    }

    @Override
    public void recordPrivate() {
        decisions.add(Decision.PRIVATE);
        check.recordPrivate();
    }
}
