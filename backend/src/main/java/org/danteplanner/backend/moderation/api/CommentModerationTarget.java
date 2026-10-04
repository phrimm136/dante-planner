package org.danteplanner.backend.moderation.api;

import java.util.UUID;

public interface CommentModerationTarget {

    ModeratedComment requireById(Long commentId);

    ModeratedComment requireByPublicId(UUID commentPublicId);

    void softDelete(Long commentId);

    Long requireReportable(UUID commentPublicId);
}
