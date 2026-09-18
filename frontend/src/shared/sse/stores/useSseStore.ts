import { create } from 'zustand'

interface SseState {
  isConnected: boolean
  lastEventTime: number | null
  reconnectAttempts: number
}

interface SseActions {
  setConnected: (connected: boolean) => void
  setLastEventTime: (time: number) => void
  incrementReconnectAttempts: () => void
  resetReconnectAttempts: () => void
}

type SseStore = SseState & SseActions

export const useSseStore = create<SseStore>((set) => ({
  isConnected: false,
  lastEventTime: null,
  reconnectAttempts: 0,

  setConnected: (connected) => set({ isConnected: connected }),

  setLastEventTime: (time) => set({ lastEventTime: time }),

  incrementReconnectAttempts: () =>
    set((state) => ({ reconnectAttempts: state.reconnectAttempts + 1 })),

  resetReconnectAttempts: () => set({ reconnectAttempts: 0 }),
}))
