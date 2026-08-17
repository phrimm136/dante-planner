import {
  FloorSelectionDraftSchema,
  validateSaveablePlanner,
  type PlannerSummary,
  type SaveablePlanner,
  type SerializableFloorSelection,
} from '@/pages/planner'
import {
  EGOGiftSpecSchema,
  toEGOGiftCardProps,
  type EGOGiftListItem,
  type EGOGiftSpec,
} from '@/pages/egoGift'
import { DUNGEON_IDX } from '@/shared/gameData'

/** The Mirror Dungeon branch of the planner union, which these factories build. */
type MDPlanner = Extract<SaveablePlanner, { config: { type: 'MIRROR_DUNGEON' } }>

const FIXTURE_PLANNER_ID = '00000000-0000-4000-8000-000000000001'
const FIXTURE_TIMESTAMP = '2026-01-01T00:00:00.000Z'
const FIXTURE_DEVICE_ID = 'fixture-device'

/** A floor selection parsed through the schema the drift guard pins to its type. */
export function buildFloorSelection(
  overrides: Partial<SerializableFloorSelection> = {},
): SerializableFloorSelection {
  return FloorSelectionDraftSchema.parse({
    themePackId: '1001',
    difficulty: DUNGEON_IDX.NORMAL,
    giftIds: [],
    ...overrides,
  })
}

/**
 * A Mirror Dungeon planner validated by `validateSaveablePlanner`, whose content
 * schemas are strict where the storage read path accepts a loose record.
 */
export function buildSaveablePlanner(
  overrides: {
    metadata?: Partial<MDPlanner['metadata']>
    config?: Partial<MDPlanner['config']>
    content?: Partial<MDPlanner['content']>
  } = {},
): SaveablePlanner {
  return validateSaveablePlanner({
    metadata: {
      id: FIXTURE_PLANNER_ID,
      title: 'Fixture Planner',
      status: 'draft',
      schemaVersion: 1,
      contentVersion: 6,
      plannerType: 'MIRROR_DUNGEON',
      syncVersion: 1,
      createdAt: FIXTURE_TIMESTAMP,
      lastModifiedAt: FIXTURE_TIMESTAMP,
      savedAt: null,
      deviceId: FIXTURE_DEVICE_ID,
      ...overrides.metadata,
    },
    config: {
      type: 'MIRROR_DUNGEON',
      category: '5F',
      ...overrides.config,
    },
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
      sectionNotes: {},
      floorSelections: [],
      ...overrides.content,
    },
  })
}

/** A planner summary composed from a planner that parses; the summary has no schema. */
export function buildPlannerSummary(overrides: Partial<PlannerSummary> = {}): PlannerSummary {
  const planner = buildSaveablePlanner()
  return {
    id: planner.metadata.id,
    title: planner.metadata.title,
    plannerType: planner.metadata.plannerType,
    category: planner.config.category,
    status: planner.metadata.status,
    lastModifiedAt: planner.metadata.lastModifiedAt,
    savedAt: planner.metadata.savedAt,
    syncVersion: planner.metadata.syncVersion,
    ...overrides,
  }
}

/**
 * A gift list item assembled the way production assembles one: a spec parsed at
 * the boundary schema, then merged into a list item by `toEGOGiftCardProps`.
 */
export function buildEgoGiftListItem(overrides: Partial<EGOGiftListItem> = {}): EGOGiftListItem {
  const { id = '9001', name = 'Fixture Gift', ...specOverrides } = overrides
  const spec = EGOGiftSpecSchema.parse({
    tag: ['TIER_3'],
    keyword: null,
    battleKeywordList: [],
    attributeType: 'WRATH',
    themePack: ['1001'],
    maxEnhancement: 2,
    ...specOverrides,
  })
  // toEGOGiftCardProps carries only the fields a card renders, so the optionals a
  // caller overrode have to be put back or the fixture silently drops them.
  return {
    ...toEGOGiftCardProps(id, spec),
    name,
    ...(spec.hardOnly === undefined ? {} : { hardOnly: spec.hardOnly }),
    ...(spec.extremeOnly === undefined ? {} : { extremeOnly: spec.extremeOnly }),
    ...(spec.fusioned === undefined ? {} : { fusioned: spec.fusioned }),
  }
}

/** A gift spec keyed by base id, parsed at the boundary schema the catalog validates with. */
export function buildEgoGiftSpecList(
  entries: Record<string, Partial<EGOGiftSpec>>,
): Record<string, EGOGiftSpec> {
  return Object.fromEntries(
    Object.entries(entries).map(([id, overrides]) => [
      id,
      EGOGiftSpecSchema.parse({
        tag: ['TIER_3'],
        keyword: null,
        battleKeywordList: [],
        attributeType: 'WRATH',
        themePack: ['1001'],
        maxEnhancement: 2,
        ...overrides,
      }),
    ]),
  )
}
