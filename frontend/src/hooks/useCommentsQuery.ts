/**
 * Comments Query Hook
 *
 * Fetches hierarchical comment tree for a planner using Suspense.
 * Tree is built server-side with deleted comments without children pruned.
 */

import { useSuspenseQuery } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { CommentTreeSchema } from '@/schemas/CommentSchemas'

import type { CommentNode } from '@/types/CommentTypes'

// ============================================================================
// Query Keys
// ============================================================================

export const commentsQueryKeys = {
  all: ['comments'] as const,
  list: (plannerId: string) => ['comments', plannerId] as const,
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook to load comment tree for a planner using Suspense
 *
 * @param plannerId - The planner ID to load comments for
 * @returns Hierarchical tree of comments (server-built)
 */
export function useCommentsQuery(plannerId: string): CommentNode[] {
  const query = useSuspenseQuery({
    queryKey: commentsQueryKeys.list(plannerId),
    queryFn: async (): Promise<CommentNode[]> => {
      const data = await ApiClient.get(`/api/planner/${plannerId}/comments`)
      const result = CommentTreeSchema.safeParse(data)

      if (!result.success) {
        console.error('Comments response validation failed:', result.error)
        throw new Error('Invalid comments response from server')
      }

      return result.data
    },
    staleTime: 30 * 1000, // 30 seconds - comments change frequently
    gcTime: 5 * 60 * 1000, // Keep in cache for 5 minutes
  })

  return query.data
}
