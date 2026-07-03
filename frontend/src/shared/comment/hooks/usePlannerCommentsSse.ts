import { useEffect, useRef, useState, useCallback } from 'react'

import { ApiClient } from '@/lib/api'

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

      es.addEventListener('comment:added', () => {
        setNewCommentsCount((c) => c + 1)
      })

      es.onerror = () => {
        es.close()
        eventSourceRef.current = null

        // Calculate exponential backoff delay
        const delay = Math.min(
          SSE_CONFIG.BASE_DELAY * Math.pow(2, reconnectAttemptsRef.current),
          SSE_CONFIG.MAX_DELAY
        )

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
  }, [plannerId])

  return {
    newCommentsCount,
    resetCount,
  }
}
