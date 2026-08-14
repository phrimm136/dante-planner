/**
 * PlannerEditorShell - teardown composition.
 *
 * The section notes are REAL here: the editor holds a keystroke for one debounce
 * interval before it reaches the store, and React destroys the shell's effects
 * before its descendants'. This file pins what the two flushes do together, which
 * neither of them can show on its own.
 */

import { describe, it, expect, vi, beforeEach, beforeAll, afterAll } from 'vitest'
import { render, fireEvent, waitFor } from '@testing-library/react'
import { ok } from '@/lib/result'
import type { Result } from '@/lib/result'
import type { SaveablePlanner } from '../../../types/PlannerTypes'
import type { SaveError } from '../../../lib/plannerSaveErrors'

const mockSaveToLocal = vi.fn<(planner: SaveablePlanner) => Promise<Result<void, SaveError>>>()
const mockGetOrCreateDeviceId = vi.fn<() => Promise<string>>()

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

vi.mock('@/pages/settings', () => ({
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

// Everything except the section notes: those are the point of this file.
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
vi.mock('../ConflictResolutionDialog', () => ({ ConflictResolutionDialog: () => null }))
vi.mock('../../SyncOffWarningDialog', () => ({ SyncOffWarningDialog: () => null }))
vi.mock('../KeywordSelector', () => ({ KeywordSelector: () => null }))

import { PlannerEditorShell } from '../PlannerEditorShell'
import { PlannerEditorStoreProvider } from '../../../stores/usePlannerEditorStore'
import { AUTO_SAVE_DEBOUNCE_MS } from '@/lib/constants'

// jsdom has no layout: ProseMirror's post-dispatch scrollToSelection calls
// Range.getClientRects(), which returns empty and throws. Shim a zero rect.
const zeroRect = {
  top: 0,
  left: 0,
  bottom: 0,
  right: 0,
  width: 0,
  height: 0,
  x: 0,
  y: 0,
  toJSON: () => ({}),
} as DOMRect
let origBounding: typeof Range.prototype.getBoundingClientRect
let origClientRects: typeof Range.prototype.getClientRects

beforeAll(() => {
  origBounding = Range.prototype.getBoundingClientRect
  origClientRects = Range.prototype.getClientRects
  Range.prototype.getBoundingClientRect = () => zeroRect
  Range.prototype.getClientRects = () =>
    ({
      length: 1,
      item: () => zeroRect,
      0: zeroRect,
      [Symbol.iterator]: () => [zeroRect][Symbol.iterator](),
    }) as unknown as DOMRectList
})

afterAll(() => {
  Range.prototype.getBoundingClientRect = origBounding
  Range.prototype.getClientRects = origClientRects
})

beforeEach(() => {
  vi.clearAllMocks()
  mockGetOrCreateDeviceId.mockResolvedValue('device-123')
  mockSaveToLocal.mockResolvedValue(ok(undefined))
})

/** Put text into the first mounted note editor, as a paste jsdom can carry. */
function typeIntoFirstNote(text: string) {
  // The editor is only editable while focused, so a paste into an untouched one
  // is discarded before it ever reaches the document.
  const container = document.querySelector('.note-editor')
  expect(container).toBeTruthy()
  fireEvent.click(container as Element)

  const contentEl = document.querySelector('.note-editor-content')
  expect(contentEl).toBeTruthy()
  fireEvent.paste(contentEl as Element, {
    clipboardData: {
      getData: (type: string) => (type === 'text/plain' ? text : ''),
      types: ['text/plain'],
      files: [],
    },
  })
}

/** Fire the event the browser fires on tab close, and report whether it was vetoed. */
function fireBeforeUnload(): boolean {
  const event = new Event('beforeunload', { cancelable: true })
  window.dispatchEvent(event)
  return event.defaultPrevented
}

function renderShell() {
  return render(
    <PlannerEditorStoreProvider>
      <PlannerEditorShell contentVersion={7} />
    </PlannerEditorStoreProvider>,
  )
}

/**
 * Mounting an editor makes Tiptap emit its parsed document once, which reaches the
 * store a debounce later and initializes the autosave baseline. Wait that out so
 * the assertions below see only what the test itself typed.
 */
async function settleMountChurn() {
  await waitFor(() => expect(document.querySelector('.note-editor-content')).toBeTruthy())
  await new Promise((resolve) => setTimeout(resolve, AUTO_SAVE_DEBOUNCE_MS * 4))
  mockSaveToLocal.mockClear()
}

describe('PlannerEditorShell - teardown with real note editors', () => {
  it('persists note text typed within one debounce of unmounting, exactly once', async () => {
    const { unmount } = renderShell()
    await settleMountChurn()

    typeIntoFirstNote('typed just before leaving')

    // No await: the editor still holds the text, and nothing has reached the store.
    unmount()

    await waitFor(() => {
      expect(mockSaveToLocal).toHaveBeenCalledTimes(1)
    })
    const written = mockSaveToLocal.mock.calls[0][0]
    expect(JSON.stringify(written)).toContain('typed just before leaving')
  })

  it('warns on a tab close within one debounce of typing a note', async () => {
    renderShell()
    await settleMountChurn()

    typeIntoFirstNote('unsaved keystrokes')

    expect(fireBeforeUnload()).toBe(true)
  })

  it('does not warn on a tab close with nothing typed', async () => {
    renderShell()
    await settleMountChurn()

    expect(fireBeforeUnload()).toBe(false)
  })
})
