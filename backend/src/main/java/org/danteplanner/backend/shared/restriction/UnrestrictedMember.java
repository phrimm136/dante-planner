package org.danteplanner.backend.shared.restriction;

import com.google.errorprone.annotations.CheckReturnValue;

import org.danteplanner.backend.shared.failure.Parsed;
import org.danteplanner.backend.shared.failure.Result;

@Parsed
public record UnrestrictedMember(Long userId) implements Clearance {

    @CheckReturnValue
    public static Result<UnrestrictedMember, Restriction.Imposed> parse(Long userId, Restriction restriction) {
        return switch (restriction) {
            case Restriction.Active active -> Result.ok(new UnrestrictedMember(userId));
            case Restriction.Imposed imposed -> Result.err(imposed);
        };
    }
}
