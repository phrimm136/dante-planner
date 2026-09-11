import { createStore } from 'zustand'
import type { StoreApi } from 'zustand'

/** What the editor's write path last reported, read only by the status label. */
export interface SaveStatus {
  /** ISO 8601 timestamp of the last write that landed, or null when none has. */
  lastSavedAt: string | null
}

export type SaveStatusStore = StoreApi<SaveStatus>

export function createSaveStatusStore(initialSavedAt: string | null): SaveStatusStore {
  return createStore<SaveStatus>(() => ({ lastSavedAt: initialSavedAt }))
}
