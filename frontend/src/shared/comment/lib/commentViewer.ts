import type { CommentNode } from '../types/CommentTypes'
import type { CommentReportReason } from '../types/CommentTypes'

export type CommentViewer = { kind: 'anon' } | { kind: 'user' } | { kind: 'moderator' }

export interface CommentActions {
  onReply: (parentCommentId: string, content: string) => void
  onEdit: (commentId: string, content: string) => void
  onDelete: (commentId: string) => void
  onModeratorDelete: (commentId: string) => void
  onUpvote: (commentId: string) => void
  onToggleNotifications: (commentId: string, enabled: boolean) => void
  onReport: (commentId: string, reason: CommentReportReason) => void
}

export function toCommentViewer(isAuthenticated: boolean, isStaff: boolean): CommentViewer {
  if (!isAuthenticated) return { kind: 'anon' }
  return isStaff ? { kind: 'moderator' } : { kind: 'user' }
}

export function canReply(viewer: CommentViewer): boolean {
  return viewer.kind !== 'anon'
}

export function canModerate(viewer: CommentViewer, comment: CommentNode): boolean {
  return viewer.kind === 'moderator' && !comment.isAuthor
}
