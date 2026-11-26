package org.danteplanner.backend.moderation.api;

import org.danteplanner.backend.shared.restriction.Clearance;
import org.danteplanner.backend.shared.restriction.RestrictionCheckHandler;

public interface RestrictionCheck extends RestrictionCheckHandler {

    void record(Clearance clearance);
}
