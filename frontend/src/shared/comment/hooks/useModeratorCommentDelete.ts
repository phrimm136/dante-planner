import { ApiClient } from '@/lib/api'
import { useApiMutation } from '@/components/hooks/useApiMutation'

export function useModeratorCommentDelete() {
  return useApiMutation<void, { commentId: string; plannerId: string; reason: string }>({
    mutationFn: async ({ commentId, reason }) => {
      await ApiClient.post(`/api/moderation/comments/${commentId}/delete`, { reason })
    },
    invalidateKeys: ({ plannerId }) => [['comments', plannerId]],
  })
}
