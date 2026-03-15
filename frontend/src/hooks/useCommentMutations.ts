/**
 * Comment Mutations Hooks
 *
 * Handles all comment mutations: create, edit, delete, upvote, report, toggle notifications.
 * Most mutations use cache invalidation. Notification toggle uses direct cache update (no refetch).
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from '@/lib/toast'

import { ApiClient, ConflictError } from '@/lib/api'
import { requestNotificationPermission } from '@/lib/browserNotification'
import { commentsQueryKeys } from './useCommentsQuery'

import type { CommentNode, CommentReportReason } from '@/types/CommentTypes'

// ============================================================================
// Create Comment
// ============================================================================

interface CreateCommentInput {
  plannerId: string
  content: string
  parentCommentId?: string // UUID
}

export function useCreateComment() {
  const queryClient = useQueryClient()
  const { t } = useTranslation()

  return useMutation({
    mutationFn: async ({
      plannerId,
      content,
      parentCommentId,
    }: CreateCommentInput): Promise<void> => {
      if (parentCommentId) {
        // Reply to existing comment
        await ApiClient.post(`/api/comments/${parentCommentId}/replies`, { content })
      } else {
        // Top-level comment
        await ApiClient.post(`/api/planner/${plannerId}/comments`, { content })
      }
    },
    onSuccess: (_, { plannerId }) => {
      void queryClient.invalidateQueries({
        queryKey: commentsQueryKeys.list(plannerId),
      })
      // Request browser notification permission on successful comment
      void requestNotificationPermission()
    },
    onError: (error) => {
      console.error('Create comment failed:', error)
      toast.error(t('comments.toast.postFailed'))
    },
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
  const queryClient = useQueryClient()
  const { t } = useTranslation()

  return useMutation({
    mutationFn: async ({ commentId, content }: EditCommentInput): Promise<void> => {
      await ApiClient.put(`/api/comments/${commentId}`, { content })
    },
    onSuccess: (_, { commentId, content, plannerId }) => {
      // Direct cache update - update content and mark as updated
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
    onError: (error) => {
      console.error('Edit comment failed:', error)
      toast.error(t('comments.toast.editFailed'))
    },
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
  const queryClient = useQueryClient()
  const { t } = useTranslation()

  return useMutation({
    mutationFn: async ({ commentId }: DeleteCommentInput): Promise<void> => {
      await ApiClient.delete(`/api/comments/${commentId}`)
    },
    onSuccess: (_, { plannerId }) => {
      // Invalidate to refetch from server - backend prunes deleted leaf comments
      void queryClient.invalidateQueries({
        queryKey: commentsQueryKeys.list(plannerId),
      })
      toast.success(t('comments.toast.deletedSuccess'))
    },
    onError: (error) => {
      console.error('Delete comment failed:', error)
      toast.error(t('comments.toast.deleteFailed'))
    },
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
  const queryClient = useQueryClient()
  const { t } = useTranslation()

  return useMutation({
    mutationFn: async ({ commentId }: UpvoteCommentInput): Promise<void> => {
      await ApiClient.post(`/api/comments/${commentId}/upvote`, {})
    },
    onSuccess: (_, { commentId, plannerId }) => {
      // Direct cache update - increment upvotes and mark as upvoted
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
  const queryClient = useQueryClient()
  const { t } = useTranslation()

  return useMutation({
    mutationFn: async ({ commentId, reason }: ReportCommentInput): Promise<void> => {
      await ApiClient.post(`/api/comments/${commentId}/report`, { reason })
    },
    onSuccess: (_, { plannerId }) => {
      void queryClient.invalidateQueries({
        queryKey: commentsQueryKeys.list(plannerId),
      })
      toast.success(t('comments.toast.reportedSuccess'))
    },
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
  const queryClient = useQueryClient()
  const { t } = useTranslation()

  return useMutation({
    mutationFn: async ({ commentId, enabled }: ToggleNotificationsInput): Promise<void> => {
      await ApiClient.patch(`/api/comments/${commentId}/notifications`, { enabled })
    },
    onSuccess: (_, { commentId, enabled, plannerId }) => {
      // Direct cache update instead of full refetch
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
    onError: (error) => {
      console.error('Toggle notifications failed:', error)
      toast.error(t('comments.toast.notificationUpdateFailed'))
    },
  })
}
