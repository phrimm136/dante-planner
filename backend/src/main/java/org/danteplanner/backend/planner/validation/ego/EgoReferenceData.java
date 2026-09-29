package org.danteplanner.backend.planner.validation.ego;

import java.util.Optional;

public interface EgoReferenceData {

    boolean hasEgo(String egoId);

    boolean belongsToSinner(String sinnerKey, String entityId);

    Optional<Integer> maxThreadspin(String egoId);
}
