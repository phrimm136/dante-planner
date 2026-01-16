import { create } from 'zustand'

/**
 * First login state
 */
interface FirstLoginState {
  /** Whether to show the sync choice dialog */
  showSyncChoiceDialog: boolean
}

/**
 * First login actions
 */
interface FirstLoginActions {
  /** Open the sync choice dialog */
  openSyncChoiceDialog: () => void
  /** Close the sync choice dialog */
  closeSyncChoiceDialog: () => void
}

type FirstLoginStore = FirstLoginState & FirstLoginActions

/**
 * Zustand store for first-login dialog state
 *
 * Controls visibility of the SyncChoiceDialog at the app level.
 * Triggered by auth callback when syncEnabled === null.
 *
 * @example
 * ```tsx
 * // In auth callback
 * const openSyncChoiceDialog = useFirstLoginStore((s) => s.openSyncChoiceDialog)
 * if (settings.syncEnabled === null) {
 *   openSyncChoiceDialog()
 * }
 *
 * // In GlobalLayout
 * const showDialog = useFirstLoginStore((s) => s.showSyncChoiceDialog)
 * <SyncChoiceDialog open={showDialog} onChoice={handleChoice} />
 * ```
 */
export const useFirstLoginStore = create<FirstLoginStore>((set) => ({
  // State
  showSyncChoiceDialog: false,

  // Actions
  openSyncChoiceDialog: () => set({ showSyncChoiceDialog: true }),
  closeSyncChoiceDialog: () => set({ showSyncChoiceDialog: false }),
}))
