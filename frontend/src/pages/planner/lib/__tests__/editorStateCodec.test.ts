import { describe, it, expect } from 'vitest'
import {
  createDefaultEquipment,
  createDefaultSectionNotes,
  createDefaultSkillEAState,
  hydrateEditorState,
  projectEditorState,
} from '../editorStateCodec'
import { DUNGEON_IDX } from '@/shared/gameData'
import { createEmptyNoteContent } from '@/shared/noteEditor'

import type { EditorMetadata } from '../editorStateCodec'
import type { MDPlannerContent } from '../../types/PlannerTypes'
import type { PlannerState } from '../../hooks/usePlannerSave'

const METADATA: EditorMetadata = { title: 'Round Trip', category: '10F', isPublished: true }

function makeContent(overrides: Partial<MDPlannerContent> = {}): MDPlannerContent {
  return {
    selectedKeywords: ['Combustion'],
    selectedBuffIds: [3],
    selectedGiftKeyword: 'fire',
    selectedGiftIds: ['g1', 'g2'],
    observationGiftIds: ['o1'],
    comprehensiveGiftIds: ['c1'],
    equipment: createDefaultEquipment(),
    deploymentOrder: [0, 1, 2],
    skillEAState: createDefaultSkillEAState(),
    floorSelections: [
      { themePackId: 'pack-1', difficulty: DUNGEON_IDX.NORMAL, giftIds: ['fg1', 'fg2'] },
    ],
    sectionNotes: { intro: { content: 'note' } },
    ...overrides,
  } as MDPlannerContent
}

/**
 * The normalized content a hydrate-project round trip is expected to produce,
 * back in the serialized shape it was read from.
 */
function serialize(state: PlannerState) {
  return {
    selectedKeywords: [...state.selectedKeywords],
    selectedBuffIds: [...state.selectedBuffIds],
    selectedGiftKeyword: state.selectedGiftKeyword,
    selectedGiftIds: [...state.selectedGiftIds],
    observationGiftIds: [...state.observationGiftIds],
    comprehensiveGiftIds: [...state.comprehensiveGiftIds],
    equipment: state.equipment,
    deploymentOrder: state.deploymentOrder,
    skillEAState: state.skillEAState,
    floorSelections: state.floorSelections.map((floor) => ({
      themePackId: floor.themePackId,
      difficulty: floor.difficulty,
      giftIds: [...floor.giftIds],
    })),
    sectionNotes: state.sectionNotes,
  }
}

function roundTrip(content: MDPlannerContent, metadata: EditorMetadata = METADATA) {
  return serialize(projectEditorState(hydrateEditorState(content, metadata)))
}

describe('hydrateEditorState / projectEditorState round trip', () => {
  const emptyNote = createEmptyNoteContent()
  const defaultNotes = createDefaultSectionNotes()
  const defaultFloors = Array.from({ length: 15 }, () => ({
    themePackId: null,
    difficulty: DUNGEON_IDX.NORMAL,
    giftIds: [] as string[],
  }))

  const cases: {
    name: string
    content: MDPlannerContent
    expected: Partial<ReturnType<typeof serialize>>
  }[] = [
    {
      name: 'full content survives the round trip unchanged',
      content: makeContent(),
      expected: {
        selectedKeywords: ['Combustion'],
        selectedBuffIds: [3],
        selectedGiftKeyword: 'fire',
        selectedGiftIds: ['g1', 'g2'],
        observationGiftIds: ['o1'],
        comprehensiveGiftIds: ['c1'],
        deploymentOrder: [0, 1, 2],
        floorSelections: [
          { themePackId: 'pack-1', difficulty: DUNGEON_IDX.NORMAL, giftIds: ['fg1', 'fg2'] },
        ],
        sectionNotes: { ...defaultNotes, intro: { content: 'note' } },
      },
    },
    {
      name: 'missing arrays normalize to their defaults',
      content: makeContent({
        selectedKeywords: undefined as unknown as string[],
        selectedBuffIds: undefined as unknown as number[],
        selectedGiftIds: undefined as unknown as string[],
        observationGiftIds: undefined as unknown as string[],
        comprehensiveGiftIds: undefined as unknown as string[],
        deploymentOrder: undefined as unknown as number[],
        floorSelections: undefined as unknown as MDPlannerContent['floorSelections'],
      }),
      expected: {
        selectedKeywords: [],
        selectedBuffIds: [],
        selectedGiftIds: [],
        observationGiftIds: [],
        comprehensiveGiftIds: [],
        deploymentOrder: [],
        floorSelections: defaultFloors,
      },
    },
    {
      name: 'non-array values normalize to their defaults',
      content: makeContent({
        selectedBuffIds: 'nope' as unknown as number[],
        comprehensiveGiftIds: {} as unknown as string[],
        deploymentOrder: 7 as unknown as number[],
      }),
      expected: {
        selectedBuffIds: [],
        comprehensiveGiftIds: [],
        deploymentOrder: [],
      },
    },
    {
      name: 'legacy keyword ids come back migrated and deduped',
      content: makeContent({
        selectedKeywords: ['AccelBullet', 'ChargeLoad', 'Combustion', 'AccelBullet'],
      }),
      expected: { selectedKeywords: ['9828', 'EmergencyChargeForceField', 'Combustion'] },
    },
    {
      name: 'empty notes fall back to the full default key set',
      content: makeContent({ sectionNotes: {} }),
      expected: { sectionNotes: defaultNotes },
    },
    {
      name: 'a missing notes map falls back to the full default key set',
      content: makeContent({
        sectionNotes: undefined as unknown as MDPlannerContent['sectionNotes'],
      }),
      expected: { sectionNotes: defaultNotes },
    },
    {
      name: 'a note with no content normalizes to an empty string',
      content: makeContent({
        sectionNotes: { outro: {} } as unknown as MDPlannerContent['sectionNotes'],
      }),
      expected: { sectionNotes: { ...defaultNotes, outro: { content: '' } } },
    },
    {
      name: 'floors keep their own defaults per missing field',
      content: makeContent({
        floorSelections: [{}] as unknown as MDPlannerContent['floorSelections'],
      }),
      expected: {
        floorSelections: [{ themePackId: null, difficulty: DUNGEON_IDX.NORMAL, giftIds: [] }],
      },
    },
  ]

  it.each(cases)('$name', ({ content, expected }) => {
    expect(roundTrip(content)).toMatchObject(expected)
  })

  it('keeps the empty-note default for sections the content never mentions', () => {
    const notes = roundTrip(
      makeContent({ sectionNotes: { intro: { content: 'only-intro' } } }),
    ).sectionNotes

    expect(notes.intro).toEqual({ content: 'only-intro' })
    expect(notes.outro).toEqual(emptyNote)
    expect(notes['floor-0']).toEqual(emptyNote)
  })

  it('falls back to the default equipment and skill EA when content omits them', () => {
    const state = hydrateEditorState(
      makeContent({
        equipment: undefined as unknown as MDPlannerContent['equipment'],
        skillEAState: undefined as unknown as MDPlannerContent['skillEAState'],
      }),
      METADATA,
    )

    expect(state.equipment).toEqual(createDefaultEquipment())
    expect(state.skillEAState).toEqual(createDefaultSkillEAState())
  })

  it('hydrates array fields into Set instances and a reset deck filter', () => {
    const state = hydrateEditorState(makeContent(), METADATA)

    expect(state.selectedKeywords).toBeInstanceOf(Set)
    expect(state.selectedBuffIds).toBeInstanceOf(Set)
    expect(state.selectedGiftIds).toBeInstanceOf(Set)
    expect(state.observationGiftIds).toBeInstanceOf(Set)
    expect(state.comprehensiveGiftIds).toBeInstanceOf(Set)
    expect(state.floorSelections[0].giftIds).toBeInstanceOf(Set)
    expect(state.deckFilterState.searchQuery).toBe('')
    expect(state.deckFilterState.entityMode).toBe('identity')
  })

  it('carries metadata through hydrate and back out of the projection', () => {
    const state = hydrateEditorState(makeContent(), METADATA)

    expect(state.isPublished).toBe(true)

    const projected = projectEditorState(state)
    expect(projected.title).toBe('Round Trip')
    expect(projected.category).toBe('10F')
    expect(projected).not.toHaveProperty('isPublished')
    expect(projected).not.toHaveProperty('deckFilterState')
  })

  it('projects the state fields by reference, without copying', () => {
    const state = hydrateEditorState(makeContent(), METADATA)
    const projected = projectEditorState(state)

    expect(projected.selectedKeywords).toBe(state.selectedKeywords)
    expect(projected.equipment).toBe(state.equipment)
    expect(projected.floorSelections).toBe(state.floorSelections)
    expect(projected.sectionNotes).toBe(state.sectionNotes)
  })
})
