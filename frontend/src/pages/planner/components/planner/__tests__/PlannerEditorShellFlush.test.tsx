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
import {
  PlannerEditorStoreProvider,
  usePlannerEditorStoreApi,
  createDefaultSectionNotes,
} from '../../../stores/usePlannerEditorStore'
import type { PlannerEditorStore, PlannerEditorState } from '../../../stores/usePlannerEditorStore'
import { AUTO_SAVE_DEBOUNCE_MS } from '@/lib/constants'
import type { StoreApi } from 'zustand'

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
  mockGetOrCreateDeviceId.mockResolvedValue(ok('device-123'))
  mockSaveToLocal.mockResolvedValue(ok(undefined))
})

/** Put text into the first mounted note editor, as a paste jsdom can carry. */
function typeIntoNote(container: Element, text: string) {
  // The editor is only editable while focused, so a paste into an untouched one
  // is discarded before it ever reaches the document.
  fireEvent.click(container)

  const contentEl = container.querySelector('.note-editor-content')
  expect(contentEl).toBeTruthy()
  fireEvent.paste(contentEl as Element, {
    clipboardData: {
      getData: (type: string) => (type === 'text/plain' ? text : ''),
      types: ['text/plain'],
      files: [],
    },
  })
}

function typeIntoFirstNote(text: string) {
  const container = document.querySelector('.note-editor')
  expect(container).toBeTruthy()
  typeIntoNote(container as Element, text)
}

/** Fire the event the browser fires on tab close, and report whether it was vetoed. */
function fireBeforeUnload(): boolean {
  const event = new Event('beforeunload', { cancelable: true })
  window.dispatchEvent(event)
  return event.defaultPrevented
}

function StoreCapture({ onReady }: { onReady: (api: StoreApi<PlannerEditorStore>) => void }) {
  onReady(usePlannerEditorStoreApi())
  return null
}

function renderShell(initialState?: Partial<PlannerEditorState>) {
  let storeApi: StoreApi<PlannerEditorStore> | null = null
  const utils = render(
    <PlannerEditorStoreProvider initialState={initialState}>
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

/**
 * Sections reveal one animation frame at a time, so wait for the whole set plus a
 * debounce interval: nothing the mount itself began is still in flight afterwards.
 *
 * Deliberately no mockClear. Mounting an editor hands Tiptap's parsed document
 * over synchronously, which is not an edit and must not write; clearing here
 * would throw away the evidence for exactly that.
 */
async function settleMount() {
  await waitFor(() => expect(document.querySelector('.note-editor-content')).toBeTruthy())
  await new Promise((resolve) => setTimeout(resolve, AUTO_SAVE_DEBOUNCE_MS * 4))
}

describe('PlannerEditorShell - teardown with real note editors', () => {
  it('persists note text typed within one debounce of unmounting, exactly once', async () => {
    const { unmount } = renderShell()
    await settleMount()

    typeIntoFirstNote('typed just before leaving')

    // No await: the editor still holds the text, and nothing has reached the store.
    unmount()

    await waitFor(() => {
      expect(mockSaveToLocal).toHaveBeenCalledTimes(1)
    })
    const written = mockSaveToLocal.mock.calls[0][0]
    expect(JSON.stringify(written)).toContain('typed just before leaving')
  })

  it('persists note text typed before the planner has written anything at all', async () => {
    // No settling: nothing has been written yet, and the editor's delivery lands
    // after the subscription is already gone, so nothing marks the planner dirty.
    // Only the baseline installed at mount can tell the flush there is work to do.
    const { unmount } = renderShell()
    await waitFor(() => expect(document.querySelector('.note-editor-content')).toBeTruthy())

    typeIntoFirstNote('typed before any baseline')

    unmount()

    await waitFor(() => {
      expect(mockSaveToLocal).toHaveBeenCalledTimes(1)
    })
    expect(JSON.stringify(mockSaveToLocal.mock.calls[0][0])).toContain('typed before any baseline')
  })

  it('warns for a note in a progressively revealed section, and keeps its text', async () => {
    // Sections reveal one animation frame at a time, so every note past the first
    // mounts in a later commit than the shell. Anything that depended on an editor
    // being registered before the shell is upside down for exactly these.
    const { storeApi } = renderShell()
    await waitFor(() => {
      expect(document.querySelectorAll('.note-editor').length).toBeGreaterThanOrEqual(3)
    })
    // Settle first, so the only thing that can arm the warning is the text below.
    await settleMount()
    expect(fireBeforeUnload()).toBe(false)

    const revealedNote = document.querySelectorAll('.note-editor')[2]
    typeIntoNote(revealedNote, 'typed in a revealed section')

    expect(fireBeforeUnload()).toBe(true)
    expect(JSON.stringify(storeApi.getState().sectionNotes)).toContain(
      'typed in a revealed section',
    )
  })

  it('warns on a tab close within one debounce of typing a note', async () => {
    renderShell()
    await settleMount()

    typeIntoFirstNote('unsaved keystrokes')

    expect(fireBeforeUnload()).toBe(true)
  })

  it('does not warn on a tab close with nothing typed', async () => {
    renderShell()
    await settleMount()

    expect(fireBeforeUnload()).toBe(false)
  })

  it('writes nothing when a stored planner whose notes are empty strings is opened', async () => {
    // What the load path actually hands over: a stored note with no content
    // becomes `{ content: '' }`, which Tiptap parses into an empty document. That
    // reparse is not an edit, however little it resembles what was stored.
    //
    // The double assertion is the point rather than a shortcut: `''` is not a
    // JSONContent, so the shape the loader produces is one the type says cannot
    // exist, and only a cast can reproduce it here.
    const emptied = Object.fromEntries(
      Object.keys(createDefaultSectionNotes()).map((key) => [key, { content: '' }]),
    ) as unknown as PlannerEditorState['sectionNotes']

    renderShell({ sectionNotes: emptied })
    await settleMount()

    expect(mockSaveToLocal).not.toHaveBeenCalled()
    expect(fireBeforeUnload()).toBe(false)
  })

  it('writes nothing when a planner is only opened', async () => {
    // Opening is not editing. Tiptap reparses every note on mount, and if that
    // travelled as an edit it would rewrite a saved planner as a draft.
    renderShell()
    await settleMount()

    expect(mockSaveToLocal).not.toHaveBeenCalled()
  })

  it('delivers pending note text on pagehide, where no warning is possible', async () => {
    const { storeApi } = renderShell()
    await settleMount()

    typeIntoFirstNote('typed before the tab was discarded')
    window.dispatchEvent(new Event('pagehide'))

    expect(JSON.stringify(storeApi.getState().sectionNotes)).toContain(
      'typed before the tab was discarded',
    )
  })
})
