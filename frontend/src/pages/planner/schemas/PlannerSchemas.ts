import { z } from 'zod'
import {
  DUNGEON_IDX,
  MAX_LEVEL,
  MD_CATEGORIES,
  RR_CATEGORIES,
  PLANNER_TYPES,
  migrateKeywords,
  IdentityIdSchema,
  EGOIdSchema,
  EncodedGiftIdSchema,
  ThemePackIdSchema,
} from '@/shared/gameData'
import type { DungeonIdx, EncodedGiftId, ThemePackId } from '@/shared/gameData'
import { JSONContentSchema } from '@/shared/noteEditor'
import { pagedModelSchema } from '@/lib/validation'
import { INITIAL_SYNC_VERSION } from '@/lib/constants'
import type {
  SerializableFloorSelection,
  SaveablePlanner,
  MDPlannerContent,
  PlannerMetadata,
  PlannerEditorConfig,
} from '../types/PlannerTypes'
import { EgoTypeSchema } from '@/shared/gameData'

export const PlannerStatusSchema = z.enum(['draft', 'saved'])

export const MDCategorySchema = z.enum(MD_CATEGORIES)

export const RRCategorySchema = z.enum(RR_CATEGORIES)

/**
 * Every category a planner of any type can carry.
 *
 * The server keys validity off the planner type (MD categories for a Mirror
 * Dungeon planner, RR categories for a Refracted Railway one), so a response
 * carrying either set is well-formed.
 */
export const PlannerCategorySchema = z.enum([...MD_CATEGORIES, ...RR_CATEGORIES])

export const DungeonIdxSchema = z.union([
  z.literal(DUNGEON_IDX.NORMAL),
  z.literal(DUNGEON_IDX.HARD),
  z.literal(DUNGEON_IDX.PARALLEL),
  z.literal(DUNGEON_IDX.EXTREME),
])

const UptieTierSchema = z.union([z.literal(1), z.literal(2), z.literal(3), z.literal(4)])

const ThreadspinTierSchema = z.union([
  z.literal(1),
  z.literal(2),
  z.literal(3),
  z.literal(4),
  z.literal(5),
])

const EquippedIdentitySchema = z
  .object({
    id: IdentityIdSchema,
    uptie: UptieTierSchema,
    level: z.number().int().min(1).max(MAX_LEVEL),
  })
  .strict()

const EquippedEGOSchema = z
  .object({
    id: EGOIdSchema,
    threadspin: ThreadspinTierSchema,
  })
  .strict()

const EGOSlotsSchema = z.partialRecord(EgoTypeSchema, EquippedEGOSchema)

const SkillEAStateSchema = z.record(z.string(), z.number())

const SinnerEquipmentSchema = z
  .object({
    identity: EquippedIdentitySchema,
    egos: EGOSlotsSchema,
  })
  .strict()

export const FloorSelectionDraftSchema = z
  .object({
    themePackId: ThemePackIdSchema.nullable(),
    difficulty: DungeonIdxSchema,
    giftIds: z.array(EncodedGiftIdSchema),
  })
  .strict()

export const FloorSelectionSaveSchema = FloorSelectionDraftSchema.extend({
  themePackId: ThemePackIdSchema,
})

export const SerializableNoteContentSchema = z
  .object({
    content: JSONContentSchema,
  })
  .strict()

export const PlannerTypeSchema = z.enum(PLANNER_TYPES)

/**
 * Metadata keys written by earlier app versions that still sit in persisted
 * planners and old export files. Dropped before the strict gate so legacy rows
 * load while unknown keys keep failing.
 */
const LEGACY_METADATA_KEYS = ['userId', 'deviceId', 'savedAt'] as const

/** Export envelope keys written by earlier app versions, likewise dropped. */
const LEGACY_ENVELOPE_KEYS = ['sourceDeviceId'] as const

const dropLegacyKeys = (keys: readonly string[]) => (value: unknown) => {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return value
  const cleaned = { ...(value as Record<string, unknown>) }
  for (const key of keys) delete cleaned[key]
  return cleaned
}

export const PlannerMetadataSchema = z.preprocess(
  dropLegacyKeys(LEGACY_METADATA_KEYS),
  z
    .object({
      id: z.string().uuid(),
      title: z.string(),
      status: PlannerStatusSchema,
      schemaVersion: z.number().int().positive(),
      contentVersion: z.number().int().positive(),
      plannerType: PlannerTypeSchema,
      syncVersion: z.number().int().positive().default(INITIAL_SYNC_VERSION),
      createdAt: z.string().datetime(),
      lastModifiedAt: z.string().datetime(),
      published: z.boolean().optional(),
    })
    .strict(),
)

export const MDConfigSchema = z
  .object({
    type: z.literal('MIRROR_DUNGEON'),
    category: MDCategorySchema,
  })
  .strict()

export const RRConfigSchema = z
  .object({
    type: z.literal('REFRACTED_RAILWAY'),
    category: RRCategorySchema,
  })
  .strict()

export const PlannerConfigDiscriminatedSchema = z.discriminatedUnion('type', [
  MDConfigSchema,
  RRConfigSchema,
])

const MDPlannerContentBaseFields = {
  selectedKeywords: z.array(z.string()),
  selectedBuffIds: z.array(z.number()),
  selectedGiftKeyword: z.string().nullable(),
  selectedGiftIds: z.array(EncodedGiftIdSchema),
  observationGiftIds: z.array(EncodedGiftIdSchema),
  comprehensiveGiftIds: z.array(EncodedGiftIdSchema),
  equipment: z.record(z.string(), SinnerEquipmentSchema),
  deploymentOrder: z.array(z.number().int().min(0).max(11)),
  skillEAState: z.record(z.string(), SkillEAStateSchema),
  sectionNotes: z.record(z.string(), SerializableNoteContentSchema),
} as const

export const MDPlannerContentDraftSchema = z
  .object({
    ...MDPlannerContentBaseFields,
    floorSelections: z.array(FloorSelectionDraftSchema),
  })
  .strict()

export const MDPlannerContentSaveSchema = z
  .object({
    ...MDPlannerContentBaseFields,
    floorSelections: z.array(FloorSelectionSaveSchema),
  })
  .strict()

export const RRPlannerContentDraftSchema = z.object({}).strict()

export const RRPlannerContentSaveSchema = RRPlannerContentDraftSchema

export const DraftPlannerSchema = z
  .object({
    metadata: PlannerMetadataSchema,
    config: PlannerConfigDiscriminatedSchema,
    content: z.record(z.string(), z.unknown()),
  })
  .strict()

export const SaveablePlannerSchema = DraftPlannerSchema

/**
 * Bind a loose content record to the config half that selects it.
 *
 * The reader schemas gate `content` as `z.record(z.string(), z.unknown())` so a
 * strict gate can never mass-discard older saves on load. This is the single
 * seam where such a record is paired with an already-narrowed `config`, and
 * therefore the only place that asserts the MD content shape without having
 * validated it field by field.
 *
 * @param metadata - Validated planner metadata
 * @param config - Narrowed planner config (carries the type discriminator)
 * @param content - Content record accepted by the loose gate
 * @returns The planner as the branch its config selects
 */
export function toSaveablePlanner(
  metadata: PlannerMetadata,
  config: PlannerEditorConfig,
  content: Record<string, unknown>,
): SaveablePlanner {
  if (config.type === 'MIRROR_DUNGEON') {
    return { metadata, config, content: content as unknown as MDPlannerContent }
  }
  return { metadata, config, content }
}

/**
 * Validate a SaveablePlanner with two-step validation:
 * 1. Validate base structure (metadata + config)
 * 2. Validate content based on config.type discriminator
 *
 * This is necessary because Zod's discriminatedUnion cannot directly
 * validate content based on a sibling field (config.type).
 *
 * @param data - Unknown data to validate
 * @param mode - 'draft' allows incomplete data, 'save' requires complete data
 * @returns Validated SaveablePlanner
 * @throws ZodError if validation fails
 *
 * @example
 * try {
 *   const planner = validateSaveablePlanner(rawData, 'draft')
 *   // planner is now typed as SaveablePlanner
 * } catch (error) {
 *   if (error instanceof z.ZodError) {
 *     console.error('Validation failed:', error.issues)
 *   }
 * }
 */
export function validateSaveablePlanner(
  data: unknown,
  mode: 'draft' | 'save' = 'draft',
): SaveablePlanner {
  const base = z
    .object({
      metadata: PlannerMetadataSchema,
      config: PlannerConfigDiscriminatedSchema,
      content: z.record(z.string(), z.unknown()),
    })
    .strict()
    .parse(data)

  if (base.config.type === 'MIRROR_DUNGEON') {
    const contentSchema = mode === 'save' ? MDPlannerContentSaveSchema : MDPlannerContentDraftSchema
    const parsed = contentSchema.parse(base.content)
    const content: MDPlannerContent = {
      ...parsed,
      // JSONContentSchema validates note bodies structurally as `unknown`, and
      // the skill-EA record is gated per key rather than per slot, so the parse
      // output is wider than MDPlannerContent on exactly these two fields.
      sectionNotes: parsed.sectionNotes as MDPlannerContent['sectionNotes'],
      skillEAState: parsed.skillEAState as MDPlannerContent['skillEAState'],
    }
    return { metadata: base.metadata, config: base.config, content }
  }

  const contentSchema = mode === 'save' ? RRPlannerContentSaveSchema : RRPlannerContentDraftSchema
  return {
    metadata: base.metadata,
    config: base.config,
    content: contentSchema.parse(base.content),
  }
}

interface PageStateWithSets {
  selectedKeywords: Set<string>
  selectedBuffIds: Set<number>
  selectedGiftIds: Set<EncodedGiftId>
  observationGiftIds: Set<EncodedGiftId>
  comprehensiveGiftIds: Set<EncodedGiftId>
  floorSelections: {
    themePackId: ThemePackId | null
    difficulty: DungeonIdx
    giftIds: Set<EncodedGiftId>
  }[]
}

interface SerializablePageState {
  selectedKeywords: string[]
  selectedBuffIds: number[]
  selectedGiftIds: EncodedGiftId[]
  observationGiftIds: EncodedGiftId[]
  comprehensiveGiftIds: EncodedGiftId[]
  floorSelections: SerializableFloorSelection[]
}

export function serializeSets(state: PageStateWithSets): SerializablePageState {
  return {
    selectedKeywords: Array.from(state.selectedKeywords),
    selectedBuffIds: Array.from(state.selectedBuffIds),
    selectedGiftIds: Array.from(state.selectedGiftIds),
    observationGiftIds: Array.from(state.observationGiftIds),
    comprehensiveGiftIds: Array.from(state.comprehensiveGiftIds),
    floorSelections: state.floorSelections.map((floor) => ({
      themePackId: floor.themePackId,
      difficulty: floor.difficulty,
      giftIds: Array.from(floor.giftIds),
    })),
  }
}

export function deserializeSets(state: SerializablePageState): PageStateWithSets {
  return {
    selectedKeywords: new Set(migrateKeywords(state.selectedKeywords)),
    selectedBuffIds: new Set(state.selectedBuffIds),
    selectedGiftIds: new Set(state.selectedGiftIds),
    observationGiftIds: new Set(state.observationGiftIds),
    comprehensiveGiftIds: new Set(state.comprehensiveGiftIds),
    floorSelections: state.floorSelections.map((floor) => ({
      themePackId: floor.themePackId,
      difficulty: floor.difficulty,
      giftIds: new Set(floor.giftIds),
    })),
  }
}

export const PlannerIdSchema = z.string().uuid().brand<'PlannerId'>()

export const LocalTombstoneSchema = z
  .object({
    id: z.string().uuid(),
    syncVersion: z.number().int().positive(),
    deletedAt: z.string(),
  })
  .strict()

export const ServerPlannerResponseSchema = z
  .object({
    id: PlannerIdSchema,
    title: z.string(),
    category: PlannerCategorySchema,
    status: PlannerStatusSchema,
    content: z.string(),
    schemaVersion: z.number().int().positive(),
    contentVersion: z.number().int().positive(),
    plannerType: PlannerTypeSchema,
    syncVersion: z.number().int().positive(),
    published: z.boolean(),
    deviceId: z.string().nullish(),
    createdAt: z.string(),
    lastModifiedAt: z.string(),
    savedAt: z.string().nullish(),
    upvotes: z.number().int().nonnegative().optional(),
  })
  .strict()

export const ServerPlannerSummarySchema = z
  .object({
    id: PlannerIdSchema,
    title: z.string(),
    category: PlannerCategorySchema,
    plannerType: PlannerTypeSchema,
    status: PlannerStatusSchema,
    syncVersion: z.number().int().positive(),
    lastModifiedAt: z.string(),
    /** Tombstone: present only on rows an includeDeleted listing adds */
    deletedAt: z.string().optional(),
  })
  .strict()

export const ServerPlannerSummaryPageSchema = pagedModelSchema(ServerPlannerSummarySchema)

/**
 * Batch pull response: a bare array, not positionally aligned with the request.
 * Ids naming nothing, a deleted planner, or another user's planner are absent.
 */
export const ServerPlannerBatchResponseSchema = z.array(ServerPlannerResponseSchema)

export const ImportPlannersResponseSchema = z
  .object({
    imported: z.number().int().nonnegative(),
    total: z.number().int().nonnegative(),
    planners: z.array(ServerPlannerSummarySchema),
  })
  .strict()

export const PlannerConfigSchema = z
  .object({
    schemaVersion: z.number().int().positive(),
    mdCurrentVersion: z.number().int().positive(),
    mdAvailableVersions: z.array(z.number().int().positive()).min(1).readonly(),
    rrAvailableVersions: z.array(z.number().int().positive()).min(1).readonly(),
  })
  .strict()

export type PlannerConfig = z.infer<typeof PlannerConfigSchema>

export const PlannerExportItemSchema = z
  .object({
    id: z.string(),
    metadata: PlannerMetadataSchema,
    config: PlannerConfigDiscriminatedSchema,
    content: z.record(z.string(), z.unknown()),
  })
  .strict()

export const ExportEnvelopeShapeSchema = z
  .object({
    exportVersion: z.number().int().positive(),
    exportedAt: z.string(),
    planners: z.array(PlannerExportItemSchema),
  })
  .strict()

export const ExportEnvelopeSchema = z.preprocess(
  dropLegacyKeys(LEGACY_ENVELOPE_KEYS),
  ExportEnvelopeShapeSchema,
)
