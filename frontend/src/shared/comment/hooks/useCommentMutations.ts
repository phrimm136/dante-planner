/**
 * Comment Mutations Hooks
 *
 * Handles all comment mutations: create, edit, delete, upvote, report, toggle notifications.
 * Most mutations use cache invalidation. Notification toggle uses direct cache update (no refetch).
 */

import { useTranslation } from 'react-i18next'
import { toast } from '@/lib/toast'

import { ApiClient, ConflictError } from '@/lib/api'
import { requestNotificationPermission } from '@/shared/notifications'
import { useApiMutation } from '@/components/hooks/useApiMutation'
import { commentsQueryKeys } from './useCommentsQuery'

import type { CommentNode, CommentReportReason } from '../types/CommentTypes'

// ============================================================================
// Create Comment
// ============================================================================

interface CreateCommentInput {
  plannerId: string
  content: string
  parentCommentId?: string // UUID
}

export function useCreateComment() {
  return useApiMutation<void, CreateCommentInput>({
    mutationFn: async ({ plannerId, content, parentCommentId }) => {
      if (parentCommentId) {
        // Reply to existing comment
        await ApiClient.post(`/api/comments/${parentCommentId}/replies`, { content })
      } else {
        // Top-level comment
        await ApiClient.post(`/api/planner/${plannerId}/comments`, { content })
      }
    },
    invalidateKeys: ({ plannerId }) => [commentsQueryKeys.list(plannerId)],
    // Request browser notification permission on successful comment
    onSuccess: () => {
      void requestNotificationPermission()
    },
    errorLogPrefix: 'Create comment failed',
    errorToastKey: 'comments.toast.postFailed',
  })
}

// ============================================================================
// Edit Comment
// ============================================================================

interface EditCommentInput {
  commentId: string // UUID
  content: string
  plannerId: string
}

export function useEditComment() {
  return useApiMutation<void, EditCommentInput>({
    mutationFn: async ({ commentId, content }) => {
      await ApiClient.put(`/api/comments/${commentId}`, { content })
    },
    // Direct cache update - update content and mark as updated
    onSuccess: (_, { commentId, content, plannerId }, queryClient) => {
      queryClient.setQueryData<CommentNode[]>(
        commentsQueryKeys.list(plannerId),
        (oldTree) => {
          if (!oldTree) return oldTree
          return updateCommentInTree(oldTree, commentId, (node) => ({
            ...node,
            content,
            isUpdated: true,
            updatedAt: new Date().toISOString(),
          }))
        }
      )
    },
    errorLogPrefix: 'Edit comment failed',
    errorToastKey: 'comments.toast.editFailed',
  })
}

// ============================================================================
// Delete Comment
// ============================================================================

interface DeleteCommentInput {
  commentId: string // UUID
  plannerId: string
}

export function useDeleteComment() {
  return useApiMutation<void, DeleteCommentInput>({
    mutationFn: async ({ commentId }) => {
      await ApiClient.delete(`/api/comments/${commentId}`)
    },
    // Invalidate to refetch from server - backend prunes deleted leaf comments
    invalidateKeys: ({ plannerId }) => [commentsQueryKeys.list(plannerId)],
    successToastKey: 'comments.toast.deletedSuccess',
    errorLogPrefix: 'Delete comment failed',
    errorToastKey: 'comments.toast.deleteFailed',
  })
}

// ============================================================================
// Upvote Comment
// ============================================================================

interface UpvoteCommentInput {
  commentId: string // UUID
  plannerId: string
}

export function useUpvoteComment() {
  const { t } = useTranslation()

  return useApiMutation<void, UpvoteCommentInput>({
    mutationFn: async ({ commentId }) => {
      await ApiClient.post(`/api/comments/${commentId}/upvote`, {})
    },
    // Direct cache update - increment upvotes and mark as upvoted
    onSuccess: (_, { commentId, plannerId }, queryClient) => {
      queryClient.setQueryData<CommentNode[]>(
        commentsQueryKeys.list(plannerId),
        (oldTree) => {
          if (!oldTree) return oldTree
          return updateCommentInTree(oldTree, commentId, (node) => ({
            ...node,
            upvoteCount: node.upvoteCount + 1,
            hasUpvoted: true,
          }))
        }
      )
    },
    onError: (error) => {
      if (error instanceof ConflictError) {
        toast.error(t('comments.toast.alreadyUpvoted'))
      } else {
        console.error('Upvote failed:', error)
        toast.error(t('comments.toast.upvoteFailed'))
      }
    },
  })
}

// ============================================================================
// Report Comment
// ============================================================================

interface ReportCommentInput {
  commentId: string // UUID
  reason: CommentReportReason
  plannerId: string
}

export function useReportComment() {
  const { t } = useTranslation()

  return useApiMutation<void, ReportCommentInput>({
    mutationFn: async ({ commentId, reason }) => {
      await ApiClient.post(`/api/comments/${commentId}/report`, { reason })
    },
    invalidateKeys: ({ plannerId }) => [commentsQueryKeys.list(plannerId)],
    successToastKey: 'comments.toast.reportedSuccess',
    onError: (error) => {
      if (error instanceof ConflictError) {
        toast.error(t('comments.toast.alreadyReported'))
      } else {
        console.error('Report failed:', error)
        toast.error(t('comments.toast.reportFailed'))
      }
    },
  })
}

// ============================================================================
// Toggle Notifications
// ============================================================================

interface ToggleNotificationsInput {
  commentId: string // UUID
  enabled: boolean
  plannerId: string
}

// ============================================================================
// Cache Update Helpers
// ============================================================================

/**
 * Recursively updates a single comment in the tree.
 * Returns a new tree with the updated node (immutable update).
 */
function updateCommentInTree(
  nodes: CommentNode[],
  targetId: string,
  updater: (node: CommentNode) => CommentNode
): CommentNode[] {
  return nodes.map((node) => {
    if (node.id === targetId) {
      return updater(node)
    }
    if (node.replies.length > 0) {
      return { ...node, replies: updateCommentInTree(node.replies, targetId, updater) }
    }
    return node
  })
}

export function useToggleCommentNotifications() {
  return useApiMutation<void, ToggleNotificationsInput>({
    mutationFn: async ({ commentId, enabled }) => {
      await ApiClient.patch(`/api/comments/${commentId}/notifications`, { enabled })
    },
    // Direct cache update instead of full refetch
    onSuccess: (_, { commentId, enabled, plannerId }, queryClient) => {
      queryClient.setQueryData<CommentNode[]>(
        commentsQueryKeys.list(plannerId),
        (oldTree) => {
          if (!oldTree) return oldTree
          return updateCommentInTree(oldTree, commentId, (node) => ({
            ...node,
            authorNotificationsEnabled: enabled,
          }))
        }
      )
    },
    errorLogPrefix: 'Toggle notifications failed',
    errorToastKey: 'comments.toast.notificationUpdateFailed',
  })
}
