import { z } from 'zod'
import { AffinitySchema, EgoTypeSchema } from '@/shared/gameData'
import {
  SkillDescEntrySchema,
  SkillIdSchema,
  PassiveIdSchema,
  EGOIdSchema,
} from '@/shared/gameData'

export { EgoTypeSchema }

export const EGOSkillDataEntrySchema = z.object({
  attributeType: z.string().optional(),
  atkType: z.string().optional(),
  targetNum: z.number().optional(),
  mpUsage: z.number().optional(),
  skillLevelCorrection: z.number().optional(),
  defaultValue: z.number().optional(),
  scale: z.number().optional(),
  coinString: z.string().optional(),
})

export const EGOSkillDataTupleSchema = z.union([
  z.tuple([
    EGOSkillDataEntrySchema,
    EGOSkillDataEntrySchema,
    EGOSkillDataEntrySchema,
    EGOSkillDataEntrySchema,
  ]),
  z.tuple([
    EGOSkillDataEntrySchema,
    EGOSkillDataEntrySchema,
    EGOSkillDataEntrySchema,
    EGOSkillDataEntrySchema,
    EGOSkillDataEntrySchema,
  ]),
])

export const EGOSkillEntrySchema = z.object({
  id: SkillIdSchema,
  skillData: EGOSkillDataTupleSchema,
})

export const EGOSkillsDataSchema = z.object({
  awaken: z.array(EGOSkillEntrySchema),
  erosion: z.array(EGOSkillEntrySchema),
})

export const EGOPassiveListTupleSchema = z.union([
  z.tuple([
    z.array(PassiveIdSchema),
    z.array(PassiveIdSchema),
    z.array(PassiveIdSchema),
    z.array(PassiveIdSchema),
  ]),
  z.tuple([
    z.array(PassiveIdSchema),
    z.array(PassiveIdSchema),
    z.array(PassiveIdSchema),
    z.array(PassiveIdSchema),
    z.array(PassiveIdSchema),
  ]),
])

export const EGOPassivesDataSchema = z.object({
  passiveList: EGOPassiveListTupleSchema,
})

export const EGODataSchema = z.object({
  updatedDate: z.number(),
  egoType: EgoTypeSchema,
  battleKeywordList: z.array(z.string()),
  season: z.number(),
  attributeResist: z.record(z.string(), z.number()),
  requirements: z.record(z.string(), z.number()),
  skills: EGOSkillsDataSchema,
  passives: EGOPassivesDataSchema,
  maxThreadspin: z.union([z.literal(4), z.literal(5)]),
})

export const EGOSkillDescEntrySchema = SkillDescEntrySchema

// `flavor` is forward-compat for when raw EGO data starts shipping it.
export const EGOSkillI18nSchema = z.object({
  name: z.string(),
  flavor: z.string().optional(),
  descs: z.array(EGOSkillDescEntrySchema),
})

export const EGOPassiveI18nSchema = z.object({
  name: z.string(),
  desc: z.string(),
  flavor: z.string().optional(),
})

export const EGOI18nSchema = z.object({
  name: z.string(),
  skills: z.record(z.string(), EGOSkillI18nSchema),
  passives: z.record(z.string(), EGOPassiveI18nSchema),
})

export const EGOAtkTypeSchema = z.enum(['SLASH', 'PENETRATE', 'HIT'])

export const EGOSpecSchema = z.object({
  updateDate: z.number(),
  skillKeywordList: z.array(z.string()),
  battleKeywordList: z.array(z.string()),
  season: z.number(),
  egoType: EgoTypeSchema,
  requirements: z.record(z.string(), z.number()),
  attributeType: z.array(AffinitySchema),
  atkType: z.array(EGOAtkTypeSchema),
  maxThreadspin: z.union([z.literal(4), z.literal(5)]),
})

export const EGOSpecListSchema = z.record(EGOIdSchema, EGOSpecSchema)
/** Keys stay unbranded: the game ships i18n-only ids with no spec entry. */
export const EGONameListSchema = z.record(z.string(), z.string())
