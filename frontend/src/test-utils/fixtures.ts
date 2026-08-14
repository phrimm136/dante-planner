import {
  FloorSelectionDraftSchema,
  validateSaveablePlanner,
  type PlannerSummary,
  type SaveablePlanner,
  type SerializableFloorSelection,
} from '@/pages/planner'
import { EGOGiftListItemSchema, type EGOGiftListItem } from '@/pages/egoGift'
import { DUNGEON_IDX } from '@/shared/gameData'

const FIXTURE_PLANNER_ID = '00000000-0000-4000-8000-000000000001'
const FIXTURE_TIMESTAMP = '2026-01-01T00:00:00.000Z'
const FIXTURE_DEVICE_ID = 'fixture-device'

/** A floor selection that has survived the same schema production parses through. */
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
 * A Mirror Dungeon planner that has survived `validateSaveablePlanner`, the same
 * ingest production parses through. Every content schema is strict, so a section
 * override is merged field-wise rather than replacing the section wholesale.
 */
export function buildSaveablePlanner(
  overrides: {
    metadata?: Record<string, unknown>
    config?: Record<string, unknown>
    content?: Record<string, unknown>
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

/**
 * `PlannerSummary` is a view type with no production schema of its own, so the
 * fields it shares with a planner are pinned to a fixture that does parse.
 */
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
 * `EGOGiftListItem` carries `battleKeywordList`, which the strict list-item schema
 * does not describe; every field the schema does describe is parsed through it.
 */
export function buildEgoGiftListItem(overrides: Partial<EGOGiftListItem> = {}): EGOGiftListItem {
  const { battleKeywordList = [], ...schemaFields } = overrides
  const parsed = EGOGiftListItemSchema.parse({
    id: '9001',
    name: 'Fixture Gift',
    tag: ['TIER_3'],
    keyword: null,
    attributeType: 'WRATH',
    themePack: ['1001'],
    maxEnhancement: 0,
    ...schemaFields,
  })
  return { ...parsed, battleKeywordList }
}
