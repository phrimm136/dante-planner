package org.danteplanner.backend.support;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.danteplanner.backend.moderation.api.RestrictionCheck;
import org.danteplanner.backend.moderation.service.RestrictionCheckService;
import org.danteplanner.backend.shared.restriction.Clearance;
import org.danteplanner.backend.shared.restriction.PrivateWrite;
import org.danteplanner.backend.shared.restriction.UnrestrictedMember;
import org.danteplanner.backend.user.api.Users;

public class RecordingRestrictionCheck implements RestrictionCheck {

    public enum Decision {
        CHECKED,
        PRIVATE
    }

    private final RestrictionCheck check;
    private final List<Decision> decisions = new ArrayList<>();
    private final List<Clearance> clearances = new ArrayList<>();

    public RecordingRestrictionCheck(Users users) {
        this.check = new RestrictionCheckService(users, Clock.systemUTC());
    }

    public List<Decision> decisions() {
        return List.copyOf(decisions);
    }

    public List<Clearance> clearances() {
        return List.copyOf(clearances);
    }

    @Override
    public UnrestrictedMember requireUnrestricted(Long userId) {
        decisions.add(Decision.CHECKED);
        return check.requireUnrestricted(userId);
    }

    @Override
    public void record(Clearance clearance) {
        switch (clearance) {
            case PrivateWrite privateWrite -> decisions.add(Decision.PRIVATE);
            case UnrestrictedMember member -> { }
        }
        clearances.add(clearance);
        check.record(clearance);
    }
}
