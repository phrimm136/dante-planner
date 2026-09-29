import {
  FloorSelectionSchema,
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
  DEFAULT_SKILL_EA,
  DUNGEON_IDX,
  IdentityIdSchema,
  EGOIdSchema,
  EGOGiftIdSchema,
  EncodedGiftIdSchema,
  SINNERS,
  SinnerScopedIdSchema,
  ThemePackIdSchema,
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
  const floor = {
    themePackId: ThemePackIdSchema.parse('1001'),
    difficulty: DUNGEON_IDX.NORMAL,
    giftIds: [],
    ...overrides,
  }
  FloorSelectionSchema.parse(floor)
  return floor
}

const FIXTURE_SINNER_KEYS = SINNERS.map((_, sinnerIndex) => String(sinnerIndex + 1))

function fixtureEquipment(): MDPlanner['content']['equipment'] {
  return Object.fromEntries(
    FIXTURE_SINNER_KEYS.map((sinnerKey) => {
      const sinnerIdPart = sinnerKey.padStart(2, '0')
      return [
        sinnerKey,
        {
          identity: { id: asIdentityId(`1${sinnerIdPart}01`), level: 1, uptie: 1 },
          egos: { ZAYIN: { id: asEGOId(`2${sinnerIdPart}01`), threadspin: 1 } },
        },
      ]
    }),
  )
}

function fixtureSkillEAState(): MDPlanner['content']['skillEAState'] {
  return Object.fromEntries(
    FIXTURE_SINNER_KEYS.map((sinnerKey) => [sinnerKey, { ...DEFAULT_SKILL_EA }]),
  )
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
      equipment: fixtureEquipment(),
      deploymentOrder: [],
      skillEAState: fixtureSkillEAState(),
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
