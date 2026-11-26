package org.danteplanner.backend.planner.precondition;

import com.google.errorprone.annotations.CheckReturnValue;
import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.planner.entity.Publication;
import org.danteplanner.backend.planner.entity.Takedown;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ContentVerdict;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.danteplanner.backend.shared.failure.Parsed;
import org.danteplanner.backend.shared.failure.Result;
import org.springframework.stereotype.Component;

@Component
@CheckReturnValue
public class PlannerPublishValidator {

    private static final ContentPath TITLE = ContentPath.of("title");

    @Parsed
    public record PublishTitle(String value) {

        public static Result<PublishTitle, ContentVerdict.Refused> parse(@Nullable String title) {
            return title == null || title.isBlank()
                    ? Result.err(new ContentVerdict.Refused(
                            new Violation(ErrorCode.MISSING_TITLE, TITLE, "Title is required for publishing")))
                    : Result.ok(new PublishTitle(title));
        }
    }

    @Parsed
    public record Publishable(Publication publication, PublishTitle title) {

        private static Publishable parse(StoredPlanner planner) {
            PublishTitle title = PublishTitle.parse(planner.content().title())
                    .orElseThrow(PlannerValidationException::refusing);
            return switch (planner.takedown()) {
                case Takedown.Clear clear -> new Publishable(planner.publication(), title);
                case Takedown.TakenDown takenDown -> throw new PlannerForbiddenException(planner.id());
            };
        }
    }

    public Publishable requirePublishable(StoredPlanner planner) {
        return Publishable.parse(planner);
    }
}
