import { create } from 'zustand'

interface FirstLoginState {
  showSyncChoiceDialog: boolean
}

interface FirstLoginActions {
  openSyncChoiceDialog: () => void
  closeSyncChoiceDialog: () => void
}

type FirstLoginStore = FirstLoginState & FirstLoginActions

export const useFirstLoginStore = create<FirstLoginStore>((set) => ({
  showSyncChoiceDialog: false,

  openSyncChoiceDialog: () => set({ showSyncChoiceDialog: true }),
  closeSyncChoiceDialog: () => set({ showSyncChoiceDialog: false }),
}))
