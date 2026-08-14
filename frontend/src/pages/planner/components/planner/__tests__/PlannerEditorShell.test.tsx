/**
 * PlannerEditorShell - unload warning.
 *
 * The shell's editing surface is stubbed out: only the beforeunload registration
 * is under test, over the real store and the real usePlannerSave (its two storage
 * adapters faked, as in usePlannerSave.test.ts).
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, act, waitFor, screen, fireEvent } from '@testing-library/react'
import type { StoreApi } from 'zustand'
import { ok } from '@/lib/result'
import type { Result } from '@/lib/result'
import type { SaveablePlanner } from '../../../types/PlannerTypes'
import type { AppError } from '@/lib/apiErrorClassifier'
import type { StorageReadError } from '@/lib/storage'

const mockSaveToLocal = vi.fn<(planner: SaveablePlanner) => Promise<Result<void, AppError>>>()
const mockGetOrCreateDeviceId = vi.fn<() => Promise<Result<string, StorageReadError>>>()

vi.mock('@/pages/planner/hooks/usePlannerStorage', () => ({
  usePlannerStorage: () => ({
    getOrCreateDeviceId: mockGetOrCreateDeviceId,
    saveToLocal: mockSaveToLocal,
    deleteFromLocal: vi.fn(),
    loadFromLocal: vi.fn(),
    listLocal: vi.fn(),
    listLocalFull: vi.fn(),
  }),
}))

vi.mock('@/pages/planner/hooks/usePlannerSyncAdapter', () => ({
  usePlannerSyncAdapter: () => ({
    syncToServer: vi.fn(),
    fetchFromServer: vi.fn(),
    deleteFromServer: vi.fn(),
    listFromServer: vi.fn(),
  }),
}))

vi.mock('@/shared/auth', () => ({
  useAuthQuery: () => ({ data: null }),
  authQueryKeys: { me: ['auth', 'me'] as const },
}))

vi.mock('@/pages/egoGift', () => ({
  useEGOGiftListData: () => ({ spec: {}, i18n: {} }),
}))

vi.mock('@/shared/userSettings', () => ({
  useUserSettingsQuery: () => ({ data: { syncEnabled: false } }),
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

vi.mock('../../../hooks/useDeckClipboard', () => ({
  useDeckClipboard: () => ({
    handleImport: vi.fn(),
    handleExport: vi.fn(),
    pendingImport: null,
    clearPending: vi.fn(),
  }),
}))

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      // The second argument is a fallback string at some call sites and
      // interpolation options at others; only the former may be returned.
      t: (key: string, fallback?: unknown) => (typeof fallback === 'string' ? fallback : key),
      i18n: { language: 'EN' },
    }),
  }
})

vi.mock('@/lib/toast', () => ({
  toast: { error: vi.fn(), success: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))

// The editing surface itself is irrelevant here, and mounting it would pull in
// every game-data query plus a Tiptap editor per section.
vi.mock('../../deckBuilder/DeckBuilderSummary', () => ({
  StoreBoundDeckBuilderSummary: () => null,
}))
vi.mock('../../deckBuilder/DeckBuilderPane', () => ({ DeckBuilderPane: () => null }))
vi.mock('../../deckBuilder/DeckBuilderContent', () => ({
  StoreBoundDeckBuilderContent: () => null,
}))
vi.mock('../../deckBuilder/DeckImportConfirmDialog', () => ({
  DeckImportConfirmDialog: () => null,
}))
vi.mock('../../startBuff/StartBuffSection', () => ({ StoreBoundStartBuffSection: () => null }))
vi.mock('../../startBuff/StartBuffEditPane', () => ({ StartBuffEditPane: () => null }))
vi.mock('../../startGift/StartGiftSummary', () => ({ StoreBoundStartGiftSummary: () => null }))
vi.mock('../../startGift/StartGiftEditPane', () => ({ StartGiftEditPane: () => null }))
vi.mock('../../egoGift/EGOGiftObservationSummary', () => ({
  StoreBoundEGOGiftObservationSummary: () => null,
}))
vi.mock('../../egoGift/EGOGiftObservationEditPane', () => ({
  EGOGiftObservationEditPane: () => null,
}))
vi.mock('../../egoGift/ComprehensiveGiftSummary', () => ({
  StoreBoundComprehensiveGiftSummary: () => null,
}))
vi.mock('../../egoGift/ComprehensiveGiftSelectorPane', () => ({
  ComprehensiveGiftSelectorPane: () => null,
}))
vi.mock('../../skillReplacement/SkillReplacementSection', () => ({
  StoreBoundSkillReplacementSection: () => null,
}))
vi.mock('../../floorTheme/FloorThemeGiftSection', () => ({ FloorThemeGiftSection: () => null }))
vi.mock('../StoreBoundSectionNote', () => ({ StoreBoundSectionNote: () => null }))
vi.mock('../ConflictResolutionDialog', () => ({ ConflictResolutionDialog: () => null }))
vi.mock('../../SyncOffWarningDialog', () => ({ SyncOffWarningDialog: () => null }))
vi.mock('../KeywordSelector', () => ({ KeywordSelector: () => null }))

import { PlannerEditorShell } from '../PlannerEditorShell'
import {
  PlannerEditorStoreProvider,
  usePlannerEditorStoreApi,
} from '../../../stores/usePlannerEditorStore'
import type { PlannerEditorStore } from '../../../stores/usePlannerEditorStore'

function StoreCapture({ onReady }: { onReady: (api: StoreApi<PlannerEditorStore>) => void }) {
  onReady(usePlannerEditorStoreApi())
  return null
}

/** Fire the event the browser fires on tab close, and report whether it was vetoed. */
function fireBeforeUnload(): boolean {
  const event = new Event('beforeunload', { cancelable: true })
  window.dispatchEvent(event)
  return event.defaultPrevented
}

function renderShell() {
  let storeApi: StoreApi<PlannerEditorStore> | null = null
  const utils = render(
    <PlannerEditorStoreProvider>
      <StoreCapture
        onReady={(api) => {
          storeApi = api
        }}
      />
      <PlannerEditorShell contentVersion={7} />
    </PlannerEditorStoreProvider>,
  )
  if (!storeApi) throw new Error('store api was not captured')
  return { ...utils, storeApi: storeApi as StoreApi<PlannerEditorStore> }
}

beforeEach(() => {
  vi.clearAllMocks()
  mockGetOrCreateDeviceId.mockResolvedValue(ok('device-123'))
  mockSaveToLocal.mockResolvedValue(ok(undefined))
})

describe('PlannerEditorShell - unload warning', () => {
  it('does not warn on a freshly mounted, untouched planner', () => {
    renderShell()

    expect(fireBeforeUnload()).toBe(false)
  })

  it('warns on a store write that has not re-rendered the shell', () => {
    const { storeApi } = renderShell()

    // deploymentOrder is not one of the slices the shell selects, so this write
    // notifies the save subscription without producing a render.
    storeApi.getState().setDeploymentOrder([3, 1, 2])

    expect(fireBeforeUnload()).toBe(true)
  })

  it('stops warning once a manual save has adopted what it wrote', async () => {
    const { storeApi } = renderShell()

    storeApi.getState().setDeploymentOrder([3, 1, 2])
    expect(fireBeforeUnload()).toBe(true)

    const saveButton = screen.getAllByRole('button', {
      name: 'pages.plannerMD.save.button',
    })[0]
    await act(async () => {
      fireEvent.click(saveButton)
    })

    await waitFor(() => {
      expect(mockSaveToLocal).toHaveBeenCalled()
    })
    expect(fireBeforeUnload()).toBe(false)
  })

  it('stops warning once the pending autosave has written', async () => {
    const { storeApi } = renderShell()

    // The first autosave only adopts a baseline; the second write is the one that
    // reaches storage.
    act(() => {
      storeApi.getState().setDeploymentOrder([3, 1, 2])
    })
    await waitFor(() => {
      expect(fireBeforeUnload()).toBe(false)
    })

    act(() => {
      storeApi.getState().setDeploymentOrder([1, 2, 3])
    })
    expect(fireBeforeUnload()).toBe(true)

    await waitFor(() => {
      expect(mockSaveToLocal).toHaveBeenCalledTimes(1)
    })
    await waitFor(() => {
      expect(fireBeforeUnload()).toBe(false)
    })
  })
})
