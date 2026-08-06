import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { COMMENT_SSE_CONNECTION, SSE_EVENTS } from '@/lib/constants'
import {
  SseEnvelopeSchema,
  useSseEngine,
  type SseConnectionState,
  type SseReconnectPolicy,
} from '@/shared/sse'

import { containsComment, insertComment } from '../lib/commentTree'
import { commentsQueryKeys } from './useCommentsQuery'

import type { CommentNode } from '../types/CommentTypes'

const COMMENT_SSE_POLICY: SseReconnectPolicy = {
  initialDelayMs: COMMENT_SSE_CONNECTION.INITIAL_DELAY,
  baseDelayMs: COMMENT_SSE_CONNECTION.BASE_DELAY,
  maxDelayMs: COMMENT_SSE_CONNECTION.MAX_DELAY,
  maxJitterMs: COMMENT_SSE_CONNECTION.MAX_JITTER,
  maxAttempts: COMMENT_SSE_CONNECTION.MAX_ATTEMPTS,
  idleResetMs: COMMENT_SSE_CONNECTION.IDLE_RESET_TIMEOUT,
  proactiveReconnectMs: COMMENT_SSE_CONNECTION.PROACTIVE_RECONNECT_INTERVAL,
  stableAfterMs: COMMENT_SSE_CONNECTION.STABLE_CONNECTION_THRESHOLD,
}

/** The count belongs to one planner, so a different planner reads zero. */
interface PlannerCommentCount {
  plannerId: string
  count: number
}

/**
 * Hook that subscribes to real-time comment notifications for a specific planner.
 *
 * When a new comment is posted on the planner by another user, the count increments
 * and the comment is grafted into the loaded tree. The author's own browser is
 * excluded (handled server-side via device ID).
 *
 * Works for both authenticated users and guests.
 *
 * @param plannerId - The planner UUID to subscribe to
 * @returns Object with newCommentsCount and resetCount function
 *
 * @example
 * ```tsx
 * function CommentSection({ plannerId }) {
 *   const { newCommentsCount, resetCount } = usePlannerCommentsSse(plannerId)
 *
 *   const handleRefresh = () => {
 *     resetCount()
 *     queryClient.invalidateQueries({ queryKey: ['comments', plannerId] })
 *   }
 *
 *   return (
 *     <>
 *       {newCommentsCount > 0 && (
 *         <button onClick={handleRefresh}>
 *           {newCommentsCount} new comments
 *         </button>
 *       )}
 *     </>
 *   )
 * }
 * ```
 */
export function usePlannerCommentsSse(plannerId: string) {
  const queryClient = useQueryClient()
  const [counted, setCounted] = useState<PlannerCommentCount>({ plannerId, count: 0 })

  // This stream is one planner's, not the app's, so it keeps its own attempt
  // counter: sharing the store's would let a flapping comment stream spend the
  // app stream's attempt budget and stretch its backoff.
  const attemptsRef = useRef(0)
  const connectionState: SseConnectionState = {
    getAttempts: () => attemptsRef.current,
    incrementAttempts: () => {
      attemptsRef.current += 1
    },
    resetAttempts: () => {
      attemptsRef.current = 0
    },
    setConnected: () => {},
  }

  const resetCount = () => {
    setCounted({ plannerId, count: 0 })
  }

  const createConnection = () =>
    ApiClient.createEventSource(`/api/planner/${plannerId}/comments/events`)

  const handleCommentAdded = (event: MessageEvent) => {
    let payload: unknown = null
    try {
      payload = SseEnvelopeSchema.parse(JSON.parse(event.data as string)).payload
    } catch (error) {
      console.warn('Planner comment SSE: failed to parse comment:added event', error)
      return
    }

    if (!payload || typeof payload !== 'object') return
    const added = payload as CommentNode
    if (typeof added.id !== 'string') return

    const listKey = commentsQueryKeys.list(plannerId)
    const cached = queryClient.getQueryData<CommentNode[]>(listKey)
    const comments = Array.isArray(cached) ? cached : []
    if (containsComment(comments, added.id)) return

    const next = insertComment(comments, added)
    if (next) {
      queryClient.setQueryData(listKey, next)
    }
    setCounted((prev) =>
      prev.plannerId === plannerId ? { plannerId, count: prev.count + 1 } : { plannerId, count: 1 },
    )
  }

  useSseEngine({
    shouldConnect: !!plannerId,
    streamKey: plannerId,
    createConnection,
    handlers: { [SSE_EVENTS.COMMENT_ADDED]: handleCommentAdded },
    policy: COMMENT_SSE_POLICY,
    state: connectionState,
  })

  return {
    newCommentsCount: counted.plannerId === plannerId ? counted.count : 0,
    resetCount,
  }
}
