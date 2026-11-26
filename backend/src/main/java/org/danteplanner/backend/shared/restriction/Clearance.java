package org.danteplanner.backend.shared.restriction;

public sealed interface Clearance permits UnrestrictedMember, PrivateWrite {
}
