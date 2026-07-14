import { useEffect, useRef, useState, useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { SSE_CONNECTION } from '@/lib/constants'
import { SseEnvelopeSchema } from '@/shared/sse'

import { commentsQueryKeys } from './useCommentsQuery'

import type { CommentNode } from '../types/CommentTypes'

/**
 * SSE reconnection configuration for planner comments
 */
const SSE_CONFIG = {
  /** Base delay for reconnection in ms */
  BASE_DELAY: 1000,
  /** Maximum delay for reconnection in ms */
  MAX_DELAY: 8000,
  /** Maximum reconnection attempts before giving up */
  MAX_ATTEMPTS: 10,
} as const

/**
 * Hook that subscribes to real-time comment notifications for a specific planner.
 *
 * When a new comment is posted on the planner by another user, the count increments.
 * The author's own browser is excluded (handled server-side via device ID).
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
  const [newCommentsCount, setNewCommentsCount] = useState(0)
  const eventSourceRef = useRef<EventSource | null>(null)
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const reconnectAttemptsRef = useRef(0)

  const resetCount = useCallback(() => {
    setNewCommentsCount(0)
  }, [])

  useEffect(() => {
    // Skip if no plannerId
    if (!plannerId) return

    const connect = () => {
      // Guard: max attempts reached
      if (reconnectAttemptsRef.current >= SSE_CONFIG.MAX_ATTEMPTS) {
        console.warn('Planner comment SSE: Max reconnection attempts reached')
        return
      }

      // Create EventSource with credentials (sends deviceId cookie)
      const es = ApiClient.createEventSource(`/api/planner/${plannerId}/comments/events`)

      eventSourceRef.current = es

      es.addEventListener('connected', () => {
        // Reset reconnect attempts on successful connection
        reconnectAttemptsRef.current = 0
      })

      es.addEventListener('comment:added', (event) => {
        setNewCommentsCount((c) => c + 1)

        try {
          const envelope = SseEnvelopeSchema.parse(JSON.parse(event.data as string))
          const payload = envelope.payload as CommentNode | null
          if (payload && typeof payload === 'object' && typeof payload.id === 'string') {
            queryClient.setQueryData(
              commentsQueryKeys.list(plannerId),
              (prev: CommentNode[] | undefined) => {
                const arr = Array.isArray(prev) ? prev : []
                return arr.some((c) => c?.id === payload.id) ? arr : [...arr, payload]
              },
            )
          }
        } catch (error) {
          console.warn('Planner comment SSE: failed to patch comment tree cache', error)
        }
      })

      es.onerror = () => {
        es.close()
        eventSourceRef.current = null

        // Calculate exponential backoff delay
        const delay =
          Math.min(
            SSE_CONFIG.BASE_DELAY * Math.pow(2, reconnectAttemptsRef.current),
            SSE_CONFIG.MAX_DELAY,
          ) +
          Math.random() * SSE_CONNECTION.MAX_JITTER

        reconnectAttemptsRef.current += 1

        // Schedule reconnect
        reconnectTimeoutRef.current = setTimeout(connect, delay)
      }
    }

    // Initial connection
    connect()

    // Cleanup on unmount or plannerId change
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close()
        eventSourceRef.current = null
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current)
        reconnectTimeoutRef.current = null
      }
      reconnectAttemptsRef.current = 0
      setNewCommentsCount(0)
    }
  }, [plannerId, queryClient])

  return {
    newCommentsCount,
    resetCount,
  }
}
