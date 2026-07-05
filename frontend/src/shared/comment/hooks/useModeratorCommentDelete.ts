import { ApiClient } from '@/lib/api'
import { useApiMutation } from '@/components/hooks/useApiMutation'

/**
 * Hook for moderator comment deletion (POST /api/moderation/comments/{id}/delete).
 * Soft-deletes the comment with reason, preserving thread structure.
 */
export function useModeratorCommentDelete() {
  return useApiMutation<void, { commentId: string; plannerId: string; reason: string }>({
    mutationFn: async ({ commentId, reason }) => {
      // commentId is public UUID (not internal ID)
      await ApiClient.post(`/api/moderation/comments/${commentId}/delete`, { reason })
    },
    // Invalidate comment queries to refetch updated tree
    invalidateKeys: ({ plannerId }) => [['comments', plannerId]],
  })
}
