import { ApiClient } from '@/lib/api'
import { ConflictError } from '@/lib/apiErrors'
import { showError, showErrorMessage } from '@/lib/errorPresentation'
import { requestNotificationPermission } from '@/shared/notifications'
import { useApiMutation } from '@/components/hooks/useApiMutation'
import { updateCommentInTree } from '../lib/commentTree'
import { commentsQueryKeys } from './useCommentsQuery'

import type { CommentNode, CommentReportReason } from '../types/CommentTypes'

interface CreateCommentInput {
  plannerId: string
  content: string
  parentCommentId?: string
}

export function useCreateComment() {
  return useApiMutation<void, CreateCommentInput>({
    mutationFn: async ({ plannerId, content, parentCommentId }) => {
      if (parentCommentId) {
        await ApiClient.post(`/api/comments/${parentCommentId}/replies`, { content })
      } else {
        await ApiClient.post(`/api/planner/${plannerId}/comments`, { content })
      }
    },
    invalidateKeys: ({ plannerId }) => [commentsQueryKeys.list(plannerId)],
    onSuccess: () => {
      void requestNotificationPermission()
    },
  })
}

interface EditCommentInput {
  commentId: string
  content: string
  plannerId: string
}

export function useEditComment() {
  return useApiMutation<void, EditCommentInput>({
    mutationFn: async ({ commentId, content }) => {
      await ApiClient.put(`/api/comments/${commentId}`, { content })
    },
    onSuccess: (_, { commentId, content, plannerId }, queryClient) => {
      queryClient.setQueryData<CommentNode[]>(commentsQueryKeys.list(plannerId), (oldTree) => {
        if (!oldTree) return oldTree
        return updateCommentInTree(oldTree, commentId, (node) => ({
          ...node,
          content,
          updatedAt: new Date().toISOString(),
        }))
      })
    },
  })
}

interface DeleteCommentInput {
  commentId: string
  plannerId: string
}

export function useDeleteComment() {
  return useApiMutation<void, DeleteCommentInput>({
    mutationFn: async ({ commentId }) => {
      await ApiClient.delete(`/api/comments/${commentId}`)
    },
    invalidateKeys: ({ plannerId }) => [commentsQueryKeys.list(plannerId)],
    successToastKey: 'common:comments.toast.deletedSuccess',
  })
}

interface UpvoteCommentInput {
  commentId: string
  plannerId: string
}

export function useUpvoteComment() {
  return useApiMutation<void, UpvoteCommentInput>({
    mutationFn: async ({ commentId }) => {
      await ApiClient.post(`/api/comments/${commentId}/upvote`, {})
    },
    onSuccess: (_, { commentId, plannerId }, queryClient) => {
      queryClient.setQueryData<CommentNode[]>(commentsQueryKeys.list(plannerId), (oldTree) => {
        if (!oldTree) return oldTree
        return updateCommentInTree(oldTree, commentId, (node) => ({
          ...node,
          upvoteCount: node.upvoteCount + 1,
          hasUpvoted: true,
        }))
      })
    },
    suppressErrorToast: true,
    onError: (error) => {
      if (error instanceof ConflictError) {
        showErrorMessage('common:comments.toast.alreadyUpvoted')
        return
      }
      showError(error)
    },
  })
}

interface ReportCommentInput {
  commentId: string
  reason: CommentReportReason
  plannerId: string
}

export function useReportComment() {
  return useApiMutation<void, ReportCommentInput>({
    mutationFn: async ({ commentId, reason }) => {
      await ApiClient.post(`/api/comments/${commentId}/report`, { reason })
    },
    invalidateKeys: ({ plannerId }) => [commentsQueryKeys.list(plannerId)],
    successToastKey: 'common:comments.toast.reportedSuccess',
    suppressErrorToast: true,
    onError: (error) => {
      if (error instanceof ConflictError) {
        showErrorMessage('common:comments.toast.alreadyReported')
        return
      }
      showError(error)
    },
  })
}

interface ToggleNotificationsInput {
  commentId: string
  enabled: boolean
  plannerId: string
}

export function useToggleCommentNotifications() {
  return useApiMutation<void, ToggleNotificationsInput>({
    mutationFn: async ({ commentId, enabled }) => {
      await ApiClient.patch(`/api/comments/${commentId}/notifications`, { enabled })
    },
    onSuccess: (_, { commentId, enabled, plannerId }, queryClient) => {
      queryClient.setQueryData<CommentNode[]>(commentsQueryKeys.list(plannerId), (oldTree) => {
        if (!oldTree) return oldTree
        return updateCommentInTree(oldTree, commentId, (node) => ({
          ...node,
          authorNotificationsEnabled: enabled,
        }))
      })
    },
  })
}
