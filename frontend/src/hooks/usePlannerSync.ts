import { useCallback, useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { plannerApi } from '@/lib/plannerApi'
import { PlannerSseEventSchema } from '@/schemas/PlannerSchemas'
import type {
  ServerPlannerResponse,
  UpsertPlannerRequest,
  ServerPlannerSummary,
} from '@/types/PlannerTypes'

/**
 * SSE event notification for i18n-compatible handling
 * Components should use this to show localized notifications
 */
export interface SseNotification {
  /** Type of SSE event received */
  type: 'created' | 'updated' | 'deleted'
  /** ID of the affected planner */
  plannerId: string
  /** Timestamp when the event was received */
  timestamp: number
}

/**
 * SSR safety check
 */
const isClient = typeof window !== 'undefined'

/**
 * Query keys for planner-related queries
 */
export const plannerQueryKeys = {
  all: ['planners'] as const,
  list: () => [...plannerQueryKeys.all, 'list'] as const,
  detail: (id: string) => [...plannerQueryKeys.all, 'detail', id] as const,
}

/**
 * Return type for usePlannerSync hook
 */
export interface PlannerSyncOperations {
  /** Connect to SSE for real-time updates */
  connectSSE: () => void
  /** Disconnect from SSE */
  disconnectSSE: () => void
  /** Create a new planner on the server */
  createPlanner: (request: UpsertPlannerRequest) => Promise<ServerPlannerResponse>
  /** Get a single planner by ID */
  getPlanner: (id: string) => Promise<ServerPlannerResponse>
  /** List all planners for the current user */
  listPlanners: () => Promise<ServerPlannerSummary[]>
  /** Delete a planner */
  deletePlanner: (id: string) => Promise<void>
  /** Latest SSE notification (for i18n-compatible UI handling) */
  sseNotification: SseNotification | null
  /** Clear the current SSE notification */
  clearSseNotification: () => void
}

/**
 * Hook for server synchronization and SSE
 *
 * Provides:
 * - SSE subscription with auto-reconnect for real-time updates
 * - CRUD operations that wrap plannerApi
 * - Query invalidation on SSE events
 *
 * Note: Device ID is managed by the backend via HTTP-only cookies.
 * The frontend does not need to store or send the device ID explicitly.
 *
 * SSE Endpoint: Uses unified /api/sse/subscribe endpoint for all event types.
 * Settings filtering is handled server-side based on user preferences.
 *
 * @example
 * ```tsx
 * function PlannerPage() {
 *   const { data: user } = useAuthQuery()
 *   const sync = usePlannerSync()
 *
 *   useEffect(() => {
 *     if (user) {
 *       sync.connectSSE()
 *     }
 *     return () => sync.disconnectSSE()
 *   }, [user])
 *
 *   const handleSave = async (planner: Planner) => {
 *     const result = await sync.updatePlanner(planner.id, {
 *       content: JSON.stringify(planner),
 *       syncVersion: planner.syncVersion,
 *     })
 *   }
 * }
 * ```
 */
export function usePlannerSync(): PlannerSyncOperations {
  const queryClient = useQueryClient()
  const eventSourceRef = useRef<EventSource | null>(null)
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const reconnectAttempts = useRef(0)

  // SSE notification state for i18n-compatible UI handling
  const [sseNotification, setSseNotification] = useState<SseNotification | null>(null)

  /**
   * Connect to SSE with auto-reconnect
   * Uses exponential backoff (1s, 2s, 4s, 8s max)
   *
   * TODO: Phase 5 - Add settings filtering integration:
   * - Pass syncEnabled/notifyEnabled to reconnect decision
   * - Reconnect immediately when settings change
   * - Server-side filtering based on user preferences
   */
  const connectSSE = () => {
    if (!isClient) return
    if (eventSourceRef.current) return // Already connected

    // SSE uses unified endpoint /api/sse/subscribe (set in plannerApi)
    const es = plannerApi.createEventsConnection()
    eventSourceRef.current = es

    es.onopen = () => {
      reconnectAttempts.current = 0
    }

    es.addEventListener('planner-update', (event) => {
      try {
        const data = PlannerSseEventSchema.parse(JSON.parse(event.data as string))
        // Invalidate queries to refresh data
        void queryClient.invalidateQueries({ queryKey: plannerQueryKeys.list() })
        void queryClient.invalidateQueries({ queryKey: plannerQueryKeys.detail(data.plannerId) })

        // Set notification for component to handle with i18n
        setSseNotification({
          type: data.type,
          plannerId: data.plannerId,
          timestamp: Date.now(),
        })
      } catch (e) {
        console.error('Failed to parse SSE event', e)
      }
    })

    es.onerror = () => {
      es.close()
      eventSourceRef.current = null

      // Reconnect with exponential backoff (1s, 2s, 4s, 8s max)
      const delay = Math.min(1000 * Math.pow(2, reconnectAttempts.current), 8000)
      reconnectAttempts.current++

      reconnectTimeoutRef.current = setTimeout(connectSSE, delay)
    }
  }

  /**
   * Disconnect from SSE and cleanup
   */
  const disconnectSSE = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current)
      reconnectTimeoutRef.current = null
    }
  }, [])

  /**
   * Cleanup on unmount
   */
  useEffect(() => {
    return () => {
      disconnectSSE()
    }
  }, [disconnectSSE])

  /**
   * Get a single planner by ID
   */
  const getPlanner = async (id: string): Promise<ServerPlannerResponse> => {
    return plannerApi.get(id)
  }

  /**
   * List all planners for the current user
   */
  const listPlanners = async (): Promise<ServerPlannerSummary[]> => {
    return plannerApi.listAll()
  }

  /**
   * Create a new planner
   */
  const createPlanner = async (request: UpsertPlannerRequest): Promise<ServerPlannerResponse> => {
    return plannerApi.upsert(request.id, request)
  }

  /**
   * Delete a planner
   * Device ID is handled by backend via HTTP-only cookie
   */
  const deletePlanner = async (id: string): Promise<void> => {
    return plannerApi.delete(id)
  }

  /**
   * Clear the current SSE notification
   */
  const clearSseNotification = () => {
    setSseNotification(null)
  }

  return {
    connectSSE,
    disconnectSSE,
    createPlanner,
    getPlanner,
    listPlanners,
    deletePlanner,
    sseNotification,
    clearSseNotification,
  }
}
