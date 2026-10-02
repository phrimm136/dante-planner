import { create } from 'zustand'

export type ServerStatus = 'ok' | 'down'

interface ServerStatusStore {
  status: ServerStatus
  setStatus: (status: ServerStatus) => void
}

export const useServerStatusStore = create<ServerStatusStore>((set) => ({
  status: 'ok',
  setStatus: (status) => set({ status }),
}))
