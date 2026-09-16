/**
 * PlannerEditorShell - write-through composition.
 *
 * The section notes are REAL here: a keystroke travels editor, store, storage,
 * and this file pins that the whole path completes inside the task that made
 * the keystroke, with no lifecycle event to help it along.
 */

import { describe, it, expect, vi, beforeEach, assert } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import { flushMicrotask, pasteIntoNote, stubRangeRects } from '@/test-utils'
import { ok } from '@/lib/result'
import type { Result } from '@/lib/result'
import type { SaveablePlanner } from '../../../types/PlannerTypes'
import type { AppError } from '@/lib/apiErrorClassifier'

const mockSaveToLocal = vi.fn<(planner: SaveablePlanner) => Promise<Result<void, AppError>>>()

vi.mock('@/pages/planner/hooks/usePlannerStorage', () => ({
  usePlannerStorage: () => ({
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

vi.mock('@/pages/egoGift', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/pages/egoGift')>()),
  useEGOGiftListSpec: () => ({ spec: {}, i18n: {} }).spec,
  useEGOGiftListI18n: () => ({ spec: {}, i18n: {} }).i18n,
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
import type { StoreApi } from 'zustand'

stubRangeRects()

beforeEach(() => {
  vi.clearAllMocks()
  mockSaveToLocal.mockResolvedValue(ok(undefined))
})

function typeIntoFirstNote(text: string) {
  const container = document.querySelector('.note-editor')
  expect(container).toBeTruthy()
  pasteIntoNote(container as Element, text)
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

/** Long enough for every section to reveal and the storage session to open. */
const MOUNT_SETTLE_MS = 800

/**
 * Sections reveal one animation frame at a time, so wait for the whole set:
 * nothing the mount itself began is still in flight afterwards.
 *
 * Deliberately no mockClear. Mounting an editor hands Tiptap's parsed document
 * over synchronously, which is not an edit and must not write; clearing here
 * would throw away the evidence for exactly that.
 */
async function settleMount() {
  await waitFor(() => expect(document.querySelector('.note-editor-content')).toBeTruthy())
  await new Promise((resolve) => setTimeout(resolve, MOUNT_SETTLE_MS))
}

const writtenJson = () => mockSaveToLocal.mock.calls.map(([planner]) => JSON.stringify(planner))

describe('PlannerEditorShell - write-through with real note editors', () => {
  it('writes note text in the task it was typed in, with no lifecycle event', async () => {
    renderShell()
    await settleMount()
    mockSaveToLocal.mockClear()

    typeIntoFirstNote('typed and immediately gone')
    await flushMicrotask()

    expect(writtenJson()).toHaveLength(1)
    expect(writtenJson()[0]).toContain('typed and immediately gone')
  })

  it('writes text typed in a progressively revealed section', async () => {
    // Sections reveal one animation frame at a time, so every note past the first
    // mounts in a later commit than the shell.
    renderShell()
    await waitFor(() => {
      expect(document.querySelectorAll('.note-editor').length).toBeGreaterThanOrEqual(3)
    })
    await settleMount()
    mockSaveToLocal.mockClear()

    const revealedNote = document.querySelectorAll('.note-editor')[2]
    assert(revealedNote, 'the shell revealed fewer than three notes')
    pasteIntoNote(revealedNote, 'typed in a revealed section')
    await flushMicrotask()

    expect(writtenJson()).toHaveLength(1)
    expect(writtenJson()[0]).toContain('typed in a revealed section')
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
  })

  it('writes nothing when a planner is only opened', async () => {
    // Opening is not editing. Tiptap reparses every note on mount, and if that
    // travelled as an edit it would rewrite a saved planner as a draft.
    renderShell()
    await settleMount()

    expect(mockSaveToLocal).not.toHaveBeenCalled()
  })

  it('registers no unload listener: nothing is ever held outside storage', async () => {
    const addSpy = vi.spyOn(window, 'addEventListener')
    renderShell()
    await settleMount()

    const unloadListeners = addSpy.mock.calls
      .map(([type]) => type)
      .filter((type) => type === 'beforeunload' || type === 'pagehide')

    expect(unloadListeners).toEqual([])
    addSpy.mockRestore()
  })
})
