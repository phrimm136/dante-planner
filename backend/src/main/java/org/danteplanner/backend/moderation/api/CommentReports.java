package org.danteplanner.backend.moderation.api;

import org.danteplanner.backend.moderation.api.dto.CommentReportRequest;
import org.danteplanner.backend.moderation.api.dto.CommentReportResponse;

import java.util.UUID;

public interface CommentReports {

    CommentReportResponse createReport(UUID commentPublicId, Long userId, CommentReportRequest request);
}
