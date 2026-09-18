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
  type EGOGiftEntity,
  type EGOGiftSpec,
} from '@/pages/egoGift'
import {
  DUNGEON_IDX,
  IdentityIdSchema,
  EGOIdSchema,
  EGOGiftIdSchema,
  EncodedGiftIdSchema,
  SinnerScopedIdSchema,
} from '@/shared/gameData'
import type { IdentityId, EGOId, EGOGiftId, EncodedGiftId, SinnerScopedId } from '@/shared/gameData'

export function asIdentityId(id: string): IdentityId {
  return IdentityIdSchema.parse(id)
}

export function asEGOId(id: string): EGOId {
  return EGOIdSchema.parse(id)
}

export function asSinnerScopedId(id: string): SinnerScopedId {
  return SinnerScopedIdSchema.parse(id)
}

export function asEGOGiftId(id: string): EGOGiftId {
  return EGOGiftIdSchema.parse(id)
}

export function asEncodedGiftId(id: string): EncodedGiftId {
  return EncodedGiftIdSchema.parse(id)
}

type MDPlanner = Extract<SaveablePlanner, { config: { type: 'MIRROR_DUNGEON' } }>

const FIXTURE_PLANNER_ID = '00000000-0000-4000-8000-000000000001'
const FIXTURE_TIMESTAMP = '2026-01-01T00:00:00.000Z'

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

export function buildPlannerSummary(overrides: Partial<PlannerSummary> = {}): PlannerSummary {
  const planner = buildSaveablePlanner()
  return {
    id: planner.metadata.id,
    title: planner.metadata.title,
    plannerType: planner.metadata.plannerType,
    category: planner.config.category,
    status: planner.metadata.status,
    lastModifiedAt: planner.metadata.lastModifiedAt,
    syncVersion: planner.metadata.syncVersion,
    ...overrides,
  }
}

export function buildEgoGiftEntity(overrides: Partial<EGOGiftEntity> = {}): EGOGiftEntity {
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
  return {
    ...toEGOGiftCardProps(id, spec),
    name,
    ...(spec.hardOnly === undefined ? {} : { hardOnly: spec.hardOnly }),
    ...(spec.extremeOnly === undefined ? {} : { extremeOnly: spec.extremeOnly }),
    ...(spec.fusioned === undefined ? {} : { fusioned: spec.fusioned }),
  }
}

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
