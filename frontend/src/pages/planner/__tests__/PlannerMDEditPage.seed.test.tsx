/**
 * Parity test for the edit page's store seed.
 *
 * `oracle` is the hand-built seed the edit page carried before it seeded through
 * the codec, transcribed verbatim; the codec must agree with it on every
 * content key for a planner that sets every content field.
 */

import { describe, it, expect } from 'vitest'
import { DUNGEON_IDX } from '@/shared/gameData'
import { ThemePackIdSchema } from '@/shared/gameData'
import { asEncodedGiftId, buildFloorSelection, buildSaveablePlanner } from '@/test-utils/fixtures'
import { createDefaultSectionNotes } from '../stores/usePlannerStore'
import { PLANNER_CONTENT_KEYS } from '../stores/__tests__/plannerContentKeys'
import { hydrateEditorState } from '../lib/editorStateCodec'
import { deserializeSets } from '../schemas/PlannerSchemas'
import { isMDPlanner } from '../types/PlannerTypes'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { PlannerStoreSeed } from '../stores/usePlannerStore'
import type { MDSaveablePlanner } from '../types/PlannerTypes'

function oracle(planner: MDSaveablePlanner): PlannerStoreSeed {
  const { content } = planner
  const deserialized = deserializeSets(content, planner.config.category)

  return {
    title: planner.metadata.title,
    category: planner.config.category,
    isPublished: planner.metadata.published ?? false,
    equipment: content.equipment,
    floorSelections: deserialized.floorSelections as FloorThemeSelection[],
    comprehensiveGiftIds: deserialized.comprehensiveGiftIds,
    deploymentOrder: content.deploymentOrder,
    selectedKeywords: deserialized.selectedKeywords,
    selectedBuffIds: deserialized.selectedBuffIds,
    selectedGiftIds: deserialized.selectedGiftIds,
    observationGiftIds: deserialized.observationGiftIds,
    selectedGiftKeyword: content.selectedGiftKeyword,
    skillEAState: content.skillEAState,
    sectionNotes: {
      ...createDefaultSectionNotes(),
      ...(content.sectionNotes
        ? Object.fromEntries(
            Object.entries(content.sectionNotes).map(([key, note]) => [
              key,
              { content: note?.content ?? '' },
            ]),
          )
        : {}),
    },
  }
}

function fullPlanner(): MDSaveablePlanner {
  const planner = buildSaveablePlanner({
    metadata: { title: 'Every field', published: true },
    config: { category: '10F' },
    content: {
      selectedKeywords: ['Combustion', 'Rupture'],
      selectedBuffIds: [101, 205],
      selectedGiftKeyword: 'Burn',
      selectedGiftIds: [asEncodedGiftId('9001')],
      observationGiftIds: [asEncodedGiftId('9002')],
      comprehensiveGiftIds: [asEncodedGiftId('9003'), asEncodedGiftId('9004')],
      deploymentOrder: [3, 0, 7],
      floorSelections: [
        buildFloorSelection({ giftIds: [asEncodedGiftId('9100')] }),
        buildFloorSelection({
          themePackId: ThemePackIdSchema.parse('1002'),
          difficulty: DUNGEON_IDX.HARD,
        }),
      ],
      sectionNotes: {
        intro: { content: { type: 'doc', content: [] } },
        outro: { content: null },
      } as unknown as MDSaveablePlanner['content']['sectionNotes'],
    },
  })
  if (!isMDPlanner(planner)) throw new Error('the fixture is not a mirror dungeon planner')
  return planner
}

describe('PlannerMDEditPage seed', () => {
  it('hydrateEditorState agrees with the hand-built seed on every content key', () => {
    const planner = fullPlanner()
    const metadata = {
      title: planner.metadata.title,
      category: planner.config.category,
      isPublished: planner.metadata.published ?? false,
    }

    const hydrated = hydrateEditorState(planner.content, metadata, 60)
    const handBuilt = oracle(planner)

    const pick = (state: PlannerStoreSeed) =>
      Object.fromEntries(PLANNER_CONTENT_KEYS.map((key) => [key, state[key]]))
    expect(pick(hydrated)).toEqual(pick(handBuilt))
  })
})
