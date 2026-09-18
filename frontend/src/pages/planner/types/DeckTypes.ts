import type { EgoType } from '@/shared/gameData'
import type {
  Affinity,
  AtkType,
  DefType,
  EGOId,
  IdentityId,
  OffensiveSkillSlot,
  Season,
  SkillAttributeType,
} from '@/shared/gameData'

export type UptieTier = 1 | 2 | 3 | 4

/**
 * Threadspin tier for EGOs (1-5). Per-EGO max — most EGOs cap at 4; some at 5.
 */
export type ThreadspinTier = 1 | 2 | 3 | 4 | 5

export interface EquippedIdentity {
  id: IdentityId
  uptie: UptieTier
  level: number
}

export interface EquippedEGO {
  id: EGOId
  threadspin: ThreadspinTier
}

export type EGOSlots = Partial<Record<EgoType, EquippedEGO>>

export type SkillEAState = Record<OffensiveSkillSlot, number>

export interface SkillInfo {
  attributeType: SkillAttributeType
  atkType?: string | undefined
}

export interface SinnerEquipment {
  identity: EquippedIdentity
  egos: EGOSlots
}

export interface DeploymentConfig {
  maxDeployed: number
}

export interface DeckState {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  deploymentConfig: DeploymentConfig
}

export interface AffinityCount {
  affinity: Affinity
  generated: number
  consumed: number
}

export interface KeywordCount {
  keyword: string
  count: number
}

export type EntityMode = 'identity' | 'ego'

export interface DeckFilterState {
  entityMode: EntityMode
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  selectedAttributes: Set<SkillAttributeType>
  selectedAtkTypes: Set<AtkType>
  selectedDefTypes: Set<DefType>
  selectedRaritys: Set<number>
  selectedEgoTypes: Set<EgoType>
  selectedSeasons: Set<Season>
  selectedUnitKeywords: Set<string>
  selectedBattleKeywords: Set<string>
  searchQuery: string
}

export type FilterSetKey = {
  [K in keyof DeckFilterState]: DeckFilterState[K] extends Set<unknown> ? K : never
}[keyof DeckFilterState]

export const FILTER_SET_KEYS = [
  'selectedSinners',
  'selectedKeywords',
  'selectedAttributes',
  'selectedAtkTypes',
  'selectedDefTypes',
  'selectedEgoTypes',
  'selectedRaritys',
  'selectedSeasons',
  'selectedUnitKeywords',
  'selectedBattleKeywords',
] as const satisfies readonly FilterSetKey[]

type Expect<T extends true> = T

export type FilterSetKeyCoverage = Expect<
  Exclude<FilterSetKey, (typeof FILTER_SET_KEYS)[number]> extends never ? true : false
>

export function createEmptyFilterSets(): Pick<DeckFilterState, FilterSetKey> {
  return Object.fromEntries(FILTER_SET_KEYS.map((key) => [key, new Set()])) as Pick<
    DeckFilterState,
    FilterSetKey
  >
}
