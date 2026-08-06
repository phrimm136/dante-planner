/**
 * Who is looking at a comment thread, and what that permits.
 *
 * The three viewer kinds are ordered by privilege — a moderator is an
 * authenticated user with the extra delete — so the pair of booleans the tree
 * used to drill (`isAuthenticated`, `isModerator`) collapses into one value
 * that cannot express a moderator who is not signed in. Whether the planner is
 * published is a property of the planner, not of the viewer, and stays its own
 * prop.
 */

import type { CommentNode } from '../types/CommentTypes'
import type { CommentReportReason } from '../types/CommentTypes'

export type CommentViewer = { kind: 'anon' } | { kind: 'user' } | { kind: 'moderator' }

/** The comment mutations a thread offers, addressed by comment id. */
export interface CommentActions {
  onReply: (parentCommentId: string, content: string) => void
  onEdit: (commentId: string, content: string) => void
  onDelete: (commentId: string) => void
  onModeratorDelete: (commentId: string) => void
  onUpvote: (commentId: string) => void
  onToggleNotifications: (commentId: string, enabled: boolean) => void
  onReport: (commentId: string, reason: CommentReportReason) => void
}

/** Derives the viewer from the auth signals the surrounding page holds. */
export function toCommentViewer(isAuthenticated: boolean, isStaff: boolean): CommentViewer {
  if (!isAuthenticated) return { kind: 'anon' }
  return isStaff ? { kind: 'moderator' } : { kind: 'user' }
}

/** Replying and composing require an account. */
export function canReply(viewer: CommentViewer): boolean {
  return viewer.kind !== 'anon'
}

/** Authors reach their own comment through the author actions, not the moderator one. */
export function canModerate(viewer: CommentViewer, comment: CommentNode): boolean {
  return viewer.kind === 'moderator' && !comment.isAuthor
}
