/**
 * Comment Mutations Hooks
 *
 * Handles all comment mutations: create, edit, delete, upvote, report, toggle notifications.
 * Most mutations use cache invalidation. Notification toggle uses direct cache update (no refetch).
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

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
      toast.error('Failed to post comment')
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

  return useMutation({
    mutationFn: async ({ commentId, content }: EditCommentInput): Promise<void> => {
      await ApiClient.put(`/api/comments/${commentId}`, { content })
    },
    onSuccess: (_, { plannerId }) => {
      void queryClient.invalidateQueries({
        queryKey: commentsQueryKeys.list(plannerId),
      })
    },
    onError: (error) => {
      console.error('Edit comment failed:', error)
      toast.error('Failed to edit comment')
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

  return useMutation({
    mutationFn: async ({ commentId }: DeleteCommentInput): Promise<void> => {
      await ApiClient.delete(`/api/comments/${commentId}`)
    },
    onSuccess: (_, { plannerId }) => {
      void queryClient.invalidateQueries({
        queryKey: commentsQueryKeys.list(plannerId),
      })
      toast.success('Comment deleted')
    },
    onError: (error) => {
      console.error('Delete comment failed:', error)
      toast.error('Failed to delete comment')
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

  return useMutation({
    mutationFn: async ({ commentId }: UpvoteCommentInput): Promise<void> => {
      await ApiClient.post(`/api/comments/${commentId}/upvote`, {})
    },
    onSuccess: (_, { plannerId }) => {
      void queryClient.invalidateQueries({
        queryKey: commentsQueryKeys.list(plannerId),
      })
    },
    onError: (error) => {
      if (error instanceof ConflictError) {
        toast.error('You have already upvoted this comment')
      } else {
        console.error('Upvote failed:', error)
        toast.error('Failed to upvote')
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

  return useMutation({
    mutationFn: async ({ commentId, reason }: ReportCommentInput): Promise<void> => {
      await ApiClient.post(`/api/comments/${commentId}/report`, { reason })
    },
    onSuccess: (_, { plannerId }) => {
      void queryClient.invalidateQueries({
        queryKey: commentsQueryKeys.list(plannerId),
      })
      toast.success('Comment reported')
    },
    onError: (error) => {
      if (error instanceof ConflictError) {
        toast.error('You have already reported this comment')
      } else {
        console.error('Report failed:', error)
        toast.error('Failed to report comment')
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

/**
 * Recursively updates a single comment's authorNotificationsEnabled in the tree.
 * Returns a new tree with the updated node (immutable update).
 */
function updateNotificationInTree(
  nodes: CommentNode[],
  targetId: string,
  enabled: boolean
): CommentNode[] {
  return nodes.map((node) => {
    if (node.id === targetId) {
      return { ...node, authorNotificationsEnabled: enabled }
    }
    if (node.replies.length > 0) {
      return { ...node, replies: updateNotificationInTree(node.replies, targetId, enabled) }
    }
    return node
  })
}

export function useToggleCommentNotifications() {
  const queryClient = useQueryClient()

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
          return updateNotificationInTree(oldTree, commentId, enabled)
        }
      )
    },
    onError: (error) => {
      console.error('Toggle notifications failed:', error)
      toast.error('Failed to update notification settings')
    },
  })
}
