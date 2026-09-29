package org.danteplanner.backend.planner.validation.identity;

public interface IdentityReferenceData {

    boolean hasIdentity(String identityId);

    boolean belongsToSinner(String sinnerKey, String entityId);
}
