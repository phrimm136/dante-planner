/**
 * Comment System Types
 *
 * Types for the comment system on published planners.
 * Comments are returned as a hierarchical tree from the backend.
 * No internal IDs exposed - uses UUID for comments, no user ID at all.
 *
 * All shapes are API-boundary types re-exported from their schemas (z.infer);
 * field documentation lives on the schema properties.
 */

export type {
  CommentNode,
  CommentVoteResponse,
  CommentReportResponse,
  CommentReportReason,
} from '../schemas/CommentSchemas'
