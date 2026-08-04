/**
 * Comments Query Hook
 *
 * Fetches hierarchical comment tree for a planner using Suspense.
 * Tree is built server-side with deleted comments without children pruned.
 */

import { useSuspenseQuery } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { CommentTreeSchema } from '../schemas/CommentSchemas'

import type { CommentNode } from '../types/CommentTypes'
import { STALE_TIME, GC_TIME } from '@/lib/constants'

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
      return validateData(data, CommentTreeSchema, `comments / ${plannerId}`)
    },
    staleTime: STALE_TIME.FREQUENT,
    gcTime: GC_TIME.SHORT,
  })

  return query.data
}
