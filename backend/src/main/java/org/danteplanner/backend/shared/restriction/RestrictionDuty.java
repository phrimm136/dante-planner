package org.danteplanner.backend.shared.restriction;

import org.springframework.web.method.HandlerMethod;

import org.danteplanner.backend.shared.security.Viewer;

public sealed interface RestrictionDuty {

    String DUTY_ATTRIBUTE = RestrictionDuty.class.getName();

    record CheckDue(Long userId) implements RestrictionDuty {
    }

    record DecisionDue(HandlerMethod handler) implements RestrictionDuty {
    }

    record Discharged() implements RestrictionDuty {
    }

    record Cleared(Clearance clearance) implements RestrictionDuty {
    }

    static RestrictionDuty of(Object handler, Viewer viewer) {
        if (!(handler instanceof HandlerMethod method)) {
            return new Discharged();
        }
        if (method.hasMethodAnnotation(Restricted.class)) {
            return switch (viewer) {
                case Viewer.Member member -> new CheckDue(member.userId());
                case Viewer.Guest guest -> new Discharged();
            };
        }
        return method.hasMethodAnnotation(RestrictedByState.class) ? new DecisionDue(method) : new Discharged();
    }
}
