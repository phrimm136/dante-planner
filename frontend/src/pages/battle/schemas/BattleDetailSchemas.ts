import { z } from 'zod'
import {
  EgoTypeSchema,
  PassiveIdSchema,
  SkillDescEntrySchema,
  SkillEntrySchema,
  SkillIdSchema,
  SkillLevelSchema,
  skillMapWithRetiredKeys,
} from '@/shared/gameData'
import { BATTLE_CASES } from '../lib/battleVocabulary'
import { BattleSpriteSchema } from './BattleSpecSchemas'

const unused = z.unknown().optional()

const StatGrowthSchema = z
  .object({
    defaultStat: z.number(),
    incrementByLevel: z.number(),
  })
  .strict()

const TypedValueSchema = z
  .object({
    type: z.string(),
    value: z.number(),
  })
  .strict()

export const ResistInfoSchema = z
  .object({
    atkResistList: z.array(TypedValueSchema),
    attributeResistList: z.array(TypedValueSchema),
  })
  .strict()

const BreakSectionSchema = z
  .object({
    sectionList: z.array(z.number()),
  })
  .strict()

const ConditionIdListSchema = z.array(z.object({ conditionID: z.string() }).strict())

const MentalConditionEntrySchema = z
  .object({
    conditionIDList: ConditionIdListSchema.optional(),
    level: z.number().optional(),
  })
  .strict()

const BattleSanitySchema = z
  .object({
    hasSanity: z.boolean(),
    canPanic: z.boolean().optional(),
    panicType: z.number().nullable().optional(),
    mentalConditionInfo: z
      .object({
        add: z.array(MentalConditionEntrySchema).optional(),
        min: z.array(MentalConditionEntrySchema).optional(),
      })
      .strict()
      .optional(),
    spMax: z.number().optional(),
    spMin: z.number().optional(),
    spStart: z.number().optional(),
    panicAt: z.number().optional(),
    lowMoraleAt: z.number().optional(),
  })
  .strict()

const PassiveImportanceSchema = z.object({ importanceLevel: z.number() }).strict()

export const PassiveImportanceMapSchema = z.record(PassiveIdSchema, PassiveImportanceSchema)

export const IdentityShapedPassivesSchema = z
  .object({
    battlePassiveList: z.array(z.array(PassiveIdSchema)),
    supportPassiveList: z.array(z.array(PassiveIdSchema)),
    conditions: z.record(
      PassiveIdSchema,
      z.object({ type: z.string(), values: z.record(z.string(), z.number()) }).strict(),
    ),
    unlock: unused,
  })
  .strict()

const RETIRED_SKILL_KEYS = ['skill1', 'skill2', 'skill3', 'skillDef'] as const

const BattleSkillLevelSchema = SkillLevelSchema.extend({ unlock: unused })

const BattleSkillEntrySchema = SkillEntrySchema.extend({
  textID: z.number().optional(),
  levels: z.tuple([BattleSkillLevelSchema], BattleSkillLevelSchema),
})

const UndefinedSkillSchema = z.object({ defined: z.literal(false) }).strict()

const BattleSkillMapSchema = z
  .object(
    Object.fromEntries(RETIRED_SKILL_KEYS.map((key) => [key, z.array(z.unknown()).optional()])),
  )
  .catchall(z.unknown())
  .transform((skills) => {
    const map: Record<string, unknown> = { ...skills }
    for (const key of RETIRED_SKILL_KEYS) delete map[key]
    return map
  })
  .pipe(z.record(SkillIdSchema, z.union([BattleSkillEntrySchema, UndefinedSkillSchema])))
  .transform((skills) =>
    Object.fromEntries(
      Object.entries(skills).filter(
        (entry): entry is [string, z.infer<typeof BattleSkillEntrySchema>] =>
          !('defined' in entry[1]),
      ),
    ),
  )

export const BattlePartSchema = z
  .object({
    id: z.number(),
    partType: z.string(),
    hp: StatGrowthSchema.optional(),
    defCorrection: z.number().optional(),
    minSpeedList: z.array(z.number()).optional(),
    maxSpeedList: z.array(z.number()).optional(),
    breakSection: BreakSectionSchema.optional(),
    resistInfo: ResistInfoSchema.optional(),
    passives: PassiveImportanceMapSchema,
    skillIds: z.array(z.number()),
    passiveIds: z.array(z.number()),
    generatedBy: z.array(z.string()).optional(),
    _source: unused,
    attributeList: unused,
    canChangeSpriteOnDestoryed: unused,
    canUseSkillOnDestroyed: unused,
    canVanish: unused,
    isDestroyable: unused,
    nameID: unused,
    originID: unused,
    overwriteSkillLevelBy: unused,
    patternID: unused,
    phaseIDList: unused,
    regenerateTurn: unused,
    sdPortrait: unused,
    showAtkLevel: unused,
    showDefLevel: unused,
    showHp: unused,
    showUnitLevel: unused,
    skillCancelable: unused,
    slotWeightConditionList: unused,
    spreadMpEffectToAbnormality: unused,
    syncOnBossRaid: unused,
    viewid: unused,
  })
  .strict()

export const BattlePhaseSchema = z
  .object({
    id: z.number(),
    spriteId: z.string(),
    sprite: BattleSpriteSchema.optional(),
    hp: StatGrowthSchema,
    defCorrection: z.number().optional(),
    minSpeedList: z.array(z.number()).optional(),
    maxSpeedList: z.array(z.number()).optional(),
    breakSection: BreakSectionSchema.optional(),
    resistInfo: ResistInfoSchema.optional(),
    unitKeywordList: z.array(z.string()).optional(),
    battleKeywordList: z.array(z.string()),
    skills: BattleSkillMapSchema,
    passives: z.union([IdentityShapedPassivesSchema, PassiveImportanceMapSchema]),
    parts: z.array(BattlePartSchema).optional(),
    patternResolvesParts: z.boolean().optional(),
    egoList: z.array(z.object({ egoID: z.number(), egoLevel: z.number() }).strict()).optional(),
    sanity: BattleSanitySchema,
    identitySource: z.number().optional(),
    characterId: z.number().optional(),
    rank: z.number().optional(),
    _source: unused,
    aggro: unused,
    appearance: unused,
    associationList: z.array(z.string()).optional(),
    attributeType: unused,
    category: unused,
    classType: unused,
    deadsceneid: unused,
    defenseSkillIDList: unused,
    hidden: unused,
    initBuffList: unused,
    maxActionSlotNum: unused,
    nameID: unused,
    overridePersonalityId: unused,
    panicSkillOnErosion: unused,
    partialUnlockSkillIdList: unused,
    patternID: unused,
    patternList: unused,
    picker: unused,
    sdPortrait: unused,
    season: unused,
    showAtkLevel: unused,
    showDefLevel: unused,
    showHp: unused,
    showUnitLevel: unused,
    skillKeywordList: unused,
    skillSlots: unused,
    slotWeightConditionList: unused,
    specialDuelRank: unused,
    startActionSlotNum: unused,
    startActionSlotNumList: unused,
    storyID: unused,
    syncOnBossRaid: unused,
    uniqueAttribute: unused,
    unitScriptID: unused,
    unlockConfigs: unused,
    updatedDate: unused,
    useUnlock: unused,
    viewid: unused,
  })
  .strict()

const BuffGrantSchema = z
  .object({
    buffId: z.number(),
    keywordId: z.string().nullable(),
    target: z.enum(['enemy', 'ally', 'unknown']),
    stack: z.number().optional(),
    conditional: z.boolean().optional(),
    derivable: z.boolean(),
    source: z
      .object({
        kind: z.enum(['stageScript', 'passive', 'buff']),
        class: z.string(),
        unitId: z.number().optional(),
        partId: z.number().optional(),
        from: z.string().optional(),
      })
      .strict(),
  })
  .strict()

const SummonSchema = z
  .object({
    kind: z.string(),
    unitId: z.number(),
    slot: z.number().optional(),
    stages: z.array(z.object({ source: z.string(), stageId: z.number() }).strict()).optional(),
  })
  .strict()

export const EngagementRowSchema = z
  .object({
    stage: z.string(),
    case: z.enum(BATTLE_CASES),
    label: z.array(z.number().int()),
    level: z.number().nullable(),
    unit: z.number(),
    wave: z.number(),
    spawn: z.string(),
    hidden: z.boolean(),
    stageScripts: z.array(z.string()),
    mapName: z.string(),
    rowParts: z.array(z.number()),
    facts: z
      .object({
        generatedParts: z.array(z.number()),
        phaseUnits: z.array(z.number()),
        summons: z.array(SummonSchema),
        buffGrants: z.array(BuffGrantSchema),
      })
      .strict(),
  })
  .strict()

const PHASES = z.tuple([BattlePhaseSchema], BattlePhaseSchema)

const EnemyKitSchema = z
  .object({
    phases: PHASES,
    badge: z
      .object({
        classType: z.string(),
        hazardGrade: z.string().optional(),
      })
      .strict()
      .optional(),
  })
  .strict()

const AssistantKitSchema = z
  .object({
    phases: PHASES,
    level: z.number().optional(),
  })
  .strict()

const SinnerEventKitSchema = z
  .object({
    phases: PHASES,
    eventCode: z.string(),
    level: z.number(),
    uptie: z.number(),
  })
  .strict()

function groupSchema<TKit extends z.ZodType>(kit: TKit) {
  return z
    .object({
      units: z.record(z.string(), kit),
      engagements: z.array(EngagementRowSchema),
    })
    .strict()
}

export const EnemyDataSchema = groupSchema(EnemyKitSchema)

export const AssistantDataSchema = groupSchema(AssistantKitSchema)

export const SinnerEventDataSchema = groupSchema(SinnerEventKitSchema)

const BattleSkillI18nSchema = z
  .object({
    name: z.string(),
    descs: z.array(SkillDescEntrySchema),
    flavor: z.string().optional(),
  })
  .strict()

const BattlePassiveI18nSchema = z
  .object({
    name: z.string().optional(),
    desc: z.string().optional(),
    flavor: z.string().optional(),
    summary: z.string().optional(),
    hidden: z.boolean().optional(),
    id: z.number().optional(),
    undefined: unused,
  })
  .strict()

const BattlePhaseI18nSchema = z
  .object({
    name: z.string().optional(),
    desc: z.string().optional(),
    skills: z.record(z.string(), BattleSkillI18nSchema).optional(),
    passives: z.record(z.string(), BattlePassiveI18nSchema).optional(),
    parts: z.record(z.string(), z.object({ name: z.string() }).strict()).optional(),
    egos: z.record(z.string(), z.object({ name: z.string() }).strict()).optional(),
    sanity: z
      .object({
        conditions: z.record(z.string(), z.string()).optional(),
        panicName: z.string().optional(),
        panicText: z.string().optional(),
        lowMoraleText: z.string().optional(),
      })
      .strict()
      .optional(),
  })
  .strict()

const BattleUnitI18nSchema = z
  .object({
    name: z.string().optional(),
    desc: z.string().optional(),
    title: z.string().optional(),
    nameWithTitle: z.string().optional(),
    phases: z.record(z.string(), BattlePhaseI18nSchema).optional(),
  })
  .strict()

export const BattleEntityI18nSchema = z
  .object({ units: z.record(z.string(), BattleUnitI18nSchema) })
  .strict()

const StageLabelSchema = z.discriminatedUnion('kind', [
  z.object({ kind: z.literal('canto'), canto: z.number(), node: z.number().optional() }).strict(),
  z
    .object({
      kind: z.literal('intervallo'),
      number: z.string(),
      part: z.string().optional(),
      node: z.number().optional(),
      difficulty: z.string().optional(),
    })
    .strict(),
  z.object({ kind: z.literal('walpurgis'), ordinal: z.number(), difficulty: z.string() }).strict(),
  z
    .object({
      kind: z.literal('storyDungeon'),
      chapter: z
        .object({
          canto: z.number().optional(),
          number: z.string().optional(),
          part: z.string().optional(),
          difficulty: z.string().optional(),
        })
        .strict(),
      dungeonNodeId: z.string().optional(),
    })
    .strict(),
  z
    .object({
      kind: z.literal('railway'),
      line: z.number(),
      node: z.number(),
      rerun: z.boolean(),
    })
    .strict(),
  z
    .object({ kind: z.literal('refractral'), bossRaid: z.number(), difficulty: z.string() })
    .strict(),
  z
    .object({
      kind: z.literal('luxcavation'),
      dungeon: z.string(),
      level: z.number(),
      threadDungeonId: z.number().optional(),
    })
    .strict(),
  z.object({ kind: z.literal('aprilFools'), titleKey: z.string() }).strict(),
  z
    .object({
      kind: z.literal('rpg'),
      canto: z.number(),
      node: z.number(),
      route: z.string(),
      floor: z.string(),
    })
    .strict(),
  z.object({ kind: z.literal('tutorial') }).strict(),
])

const BattleStageSchema = z
  .object({
    source: z.string(),
    stageId: z.number(),
    case: z.string(),
    kind: z.string(),
    stageLevel: z.number(),
    waveCount: z.number(),
    maps: z.array(z.string()),
    labels: z.array(StageLabelSchema).optional(),
    mirror: z
      .object({
        themeIds: z.array(z.number()).optional(),
        eventIds: z.array(z.string()).optional(),
        hiddenBattle: z.boolean().optional(),
        poolKind: unused,
        dungeonId: unused,
        typeIndex: unused,
        floors: unused,
        section: unused,
      })
      .strict()
      .optional(),
    stageType: unused,
    recommendedLevel: unused,
    turnLimit: unused,
    staminaCost: unused,
    dangerLevel: unused,
    eventCode: unused,
    railway: unused,
    includeBoss: unused,
    bossRaid: unused,
  })
  .strict()

export const BattleStageListSchema = z.record(z.string(), BattleStageSchema)

const StageLabelTextSchema = z
  .object({
    title: z.string().optional(),
    line: z.string().optional(),
    node: z.string().optional(),
    dungeon: z.string().optional(),
    dungeonNode: z.string().optional(),
  })
  .strict()

export const BattleStageTextsSchema = z.record(
  z.string(),
  z
    .object({
      title: z.string().optional(),
      place: z.string().optional(),
      themeNames: z.array(z.string()).optional(),
      labelTexts: z.array(StageLabelTextSchema).optional(),
    })
    .strict(),
)

export const BattleEgoDataSchema = z.object({
  egoType: EgoTypeSchema,
  skills: skillMapWithRetiredKeys(['awaken', 'erosion']),
  passives: z.object({ passiveList: z.array(z.array(PassiveIdSchema)) }),
})

export const BattleEgoI18nSchema = z.object({
  name: z.string(),
  skills: z.record(
    z.string(),
    z.object({ name: z.string(), descs: z.array(SkillDescEntrySchema) }),
  ),
  passives: z.record(z.string(), z.object({ name: z.string(), desc: z.string() })),
})

export type BattlePart = z.infer<typeof BattlePartSchema>
export type BattlePhase = z.infer<typeof BattlePhaseSchema>
export type EngagementRow = z.infer<typeof EngagementRowSchema>
export type BuffGrant = z.infer<typeof BuffGrantSchema>
export type EnemyData = z.infer<typeof EnemyDataSchema>
export type AssistantData = z.infer<typeof AssistantDataSchema>
export type SinnerEventData = z.infer<typeof SinnerEventDataSchema>
export type EnemyKit = z.infer<typeof EnemyKitSchema>
export type BattleKit = EnemyKit & { level?: number | undefined; uptie?: number | undefined }
export type BattleEntityI18n = z.infer<typeof BattleEntityI18nSchema>
export type BattleUnitI18n = z.infer<typeof BattleUnitI18nSchema>
export type BattlePhaseI18n = z.infer<typeof BattlePhaseI18nSchema>
export type ResistInfo = z.infer<typeof ResistInfoSchema>
export type StageLabel = z.infer<typeof StageLabelSchema>
export type StageLabelText = z.infer<typeof StageLabelTextSchema>
export type BattleStageList = z.infer<typeof BattleStageListSchema>
export type BattleStageTexts = z.infer<typeof BattleStageTextsSchema>
export type IdentityShapedPassives = z.infer<typeof IdentityShapedPassivesSchema>
export type PassiveImportanceMap = z.infer<typeof PassiveImportanceMapSchema>
export type BattleEgoData = z.infer<typeof BattleEgoDataSchema>
export type BattleEgoI18n = z.infer<typeof BattleEgoI18nSchema>
