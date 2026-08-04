/**
 * DOM snapshots of both planner viewers, one row per note layout that changes
 * what they render.
 *
 * The committed snapshots were produced by an innerHTML comparison against the
 * pre-descriptor viewers, so they record their markup exactly.
 */

import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'

import { GuideModeViewer } from '../GuideModeViewer'
import { TrackerModeViewer } from '../TrackerModeViewer'

import type { SaveablePlanner, MDPlannerContent } from '../../../types/PlannerTypes'
import type { NoteContent } from '@/shared/noteEditor'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'EN' } }),
  }
})

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

// Every visual child is stubbed: this test pins the viewers' own composition,
// not the sections they host.
vi.mock('../../deckBuilder/DeckBuilderSummary', () => ({
  DeckBuilderSummary: () => <div data-testid="deck-summary" />,
}))
vi.mock('../../deckBuilder/DeckBuilderPane', () => ({
  DeckBuilderPane: () => <div data-testid="deck-pane" />,
}))
vi.mock('../../startBuff/StartBuffSection', () => ({
  StartBuffSection: () => <div data-testid="start-buff" />,
}))
vi.mock('../../startGift/StartGiftSummary', () => ({
  StartGiftSummary: () => <div data-testid="start-gift" />,
}))
vi.mock('../../egoGift/EGOGiftObservationSummary', () => ({
  EGOGiftObservationSummary: () => <div data-testid="observation" />,
}))
vi.mock('../../skillReplacement/SkillReplacementSection', () => ({
  SkillReplacementSection: () => <div data-testid="skill-replacement" />,
}))
vi.mock('../ComprehensiveGiftGridTracker', () => ({
  ComprehensiveGiftGridTracker: () => <div data-testid="comprehensive" />,
}))
vi.mock('../FloorGalleryTracker', () => ({
  FloorGalleryTracker: () => <div data-testid="floor-gallery" />,
}))
vi.mock('../HorizontalThemePackGallery', () => ({
  HorizontalThemePackGallery: () => <div data-testid="theme-gallery" />,
}))
vi.mock('../DeckTrackerPanel', () => ({
  DeckTrackerPanel: () => <div data-testid="deck-tracker" />,
}))
vi.mock('../../SectionNoteDialog', () => ({
  SectionNoteDialog: ({ open, sectionTitle }: { open: boolean; sectionTitle: string }) =>
    open ? <div data-testid={`note-dialog-${sectionTitle}`} /> : null,
}))
vi.mock('@/shared/noteEditor/components/NoteEditor', () => ({
  NoteEditor: ({ value }: { value: NoteContent }) => (
    <div data-testid="note-editor">{JSON.stringify(value)}</div>
  ),
}))
vi.mock('@/pages/identity', () => ({ useIdentityListSpec: () => ({}) }))
vi.mock('@/pages/ego', () => ({ useEGOListSpec: () => ({}) }))

// All sections revealed, so every branch of the descriptor loop is exercised.
vi.mock('@/components/hooks/useProgressiveReveal', () => ({
  useProgressiveReveal: (count: number) => Array.from({ length: count }, () => true),
}))

function note(text: string): NoteContent {
  return {
    content: {
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text }] }],
    },
  } as NoteContent
}

const EMPTY_NOTE: NoteContent = { content: { type: 'doc', content: [] } } as NoteContent

function makePlanner(sectionNotes: Record<string, NoteContent>, category = '5F'): SaveablePlanner {
  return {
    metadata: {
      id: 'p1',
      title: 'Plan',
      status: 'saved',
      schemaVersion: 2,
      contentVersion: 7,
      plannerType: 'MIRROR_DUNGEON',
      syncVersion: 1,
      createdAt: '2026-01-01T00:00:00Z',
      lastModifiedAt: '2026-01-01T00:00:00Z',
      savedAt: '2026-01-01T00:00:00Z',
      deviceId: 'd1',
      published: false,
    },
    config: { type: 'MIRROR_DUNGEON', category },
    content: {
      selectedKeywords: [],
      selectedBuffIds: [],
      selectedGiftKeyword: null,
      selectedGiftIds: [],
      observationGiftIds: [],
      comprehensiveGiftIds: [],
      equipment: {},
      deploymentOrder: [],
      skillEAState: {},
      floorSelections: [],
      sectionNotes,
    } as MDPlannerContent,
  } as SaveablePlanner
}

const ALL_KEYS = [
  'intro',
  'deckBuilder',
  'startBuffs',
  'startGifts',
  'observation',
  'skillReplacement',
  'comprehensiveGifts',
  'outro',
]

const MATRIX: Array<[string, SaveablePlanner]> = [
  ['no notes at all', makePlanner({})],
  ['every note filled', makePlanner(Object.fromEntries(ALL_KEYS.map((key) => [key, note(key)])))],
  [
    'every note present but empty',
    makePlanner(Object.fromEntries(ALL_KEYS.map((key) => [key, EMPTY_NOTE]))),
  ],
  ['only intro and outro', makePlanner({ intro: note('intro'), outro: note('outro') })],
  [
    'only middle-section notes',
    makePlanner({ startGifts: note('gifts'), skillReplacement: note('skills') }),
  ],
  [
    'a 15F plan with notes',
    makePlanner(Object.fromEntries(ALL_KEYS.map((key) => [key, note(key)])), '15F'),
  ],
]

describe('GuideModeViewer DOM', () => {
  it.each(MATRIX)('renders %s', (_label, planner) => {
    const next = render(<GuideModeViewer planner={planner} />)

    expect(next.container.innerHTML).toMatchSnapshot()
  })
})

describe('TrackerModeViewer DOM', () => {
  it.each(MATRIX)('renders %s', (_label, planner) => {
    const next = render(<TrackerModeViewer planner={planner} />)

    expect(next.container.innerHTML).toMatchSnapshot()
  })
})
