import { create } from 'zustand'

/**
 * SSE connection state
 */
interface SseState {
  /** Whether SSE is currently connected */
  isConnected: boolean
  /** Timestamp of last received event */
  lastEventTime: number | null
  /** Number of reconnection attempts since last successful connection */
  reconnectAttempts: number
}

/**
 * SSE store actions
 */
interface SseActions {
  /** Update connection status */
  setConnected: (connected: boolean) => void
  /** Update last event timestamp */
  setLastEventTime: (time: number) => void
  /** Increment reconnect attempt counter */
  incrementReconnectAttempts: () => void
  /** Reset reconnect counter (on successful connection) */
  resetReconnectAttempts: () => void
}

type SseStore = SseState & SseActions

/**
 * Zustand store for SSE connection state
 *
 * Tracks connection status, event timing, and reconnection attempts.
 * Used by usePlannerSync for connection management.
 *
 * @example
 * ```tsx
 * // Subscribe to specific slice only
 * const isConnected = useSseStore((s) => s.isConnected)
 * const setConnected = useSseStore((s) => s.setConnected)
 * ```
 */
export const useSseStore = create<SseStore>((set) => ({
  // State
  isConnected: false,
  lastEventTime: null,
  reconnectAttempts: 0,

  // Actions
  setConnected: (connected) => set({ isConnected: connected }),

  setLastEventTime: (time) => set({ lastEventTime: time }),

  incrementReconnectAttempts: () =>
    set((state) => ({ reconnectAttempts: state.reconnectAttempts + 1 })),

  resetReconnectAttempts: () => set({ reconnectAttempts: 0 }),
}))
