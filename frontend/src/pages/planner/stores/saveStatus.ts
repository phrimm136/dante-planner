import { createStore } from 'zustand'
import type { StoreApi } from 'zustand'

export interface SaveStatus {
  lastSavedAt: string | null
}

export type SaveStatusStore = StoreApi<SaveStatus>

export function createSaveStatusStore(initialSavedAt: string | null): SaveStatusStore {
  return createStore<SaveStatus>(() => ({ lastSavedAt: initialSavedAt }))
}
