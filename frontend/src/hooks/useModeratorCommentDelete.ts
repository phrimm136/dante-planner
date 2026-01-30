import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiClient } from '@/lib/api'

/**
 * Hook for moderator comment deletion (POST /api/moderation/comments/{id}/delete).
 * Soft-deletes the comment with reason, preserving thread structure.
 */
export function useModeratorCommentDelete() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({ commentId, plannerId: _plannerId, reason }: { commentId: string; plannerId: string; reason: string }) => {
      // commentId is public UUID (not internal ID)
      await ApiClient.post(`/api/moderation/comments/${commentId}/delete`, { reason })
    },
    onSuccess: (_data, variables) => {
      // Invalidate comment queries to refetch updated tree
      void queryClient.invalidateQueries({ queryKey: ['comments', variables.plannerId] })
    },
  })
}
