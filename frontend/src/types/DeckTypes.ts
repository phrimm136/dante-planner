import type { EGOType } from './EGOTypes'
import type { Affinity, AtkType, DefType, OffensiveSkillSlot, Season, SkillAttributeType } from '@/lib/constants'

/**
 * Uptie tier for identities (1-4)
 */
export type UptieTier = 1 | 2 | 3 | 4

/**
 * Threadspin tier for EGOs (1-4)
 */
export type ThreadspinTier = 1 | 2 | 3 | 4

/**
 * Equipped identity configuration for a sinner
 */
export interface EquippedIdentity {
  id: string
  uptie: UptieTier
  level: number
}

/**
 * Equipped EGO configuration
 */
export interface EquippedEGO {
  id: string
  threadspin: ThreadspinTier
}

/**
 * EGO slots by rank for a sinner
 */
export type EGOSlots = Partial<Record<EGOType, EquippedEGO>>

/**
 * Skill EA (Exchange Allowance) state per offensive skill slot
 * Records remaining EA for each skill (default: S1=3, S2=2, S3=1)
 */
export type SkillEAState = Record<OffensiveSkillSlot, number>

/**
 * Skill info for display (attribute type and attack type)
 */
export interface SkillInfo {
  attributeType: SkillAttributeType
  atkType?: string
}

/**
 * Complete equipment configuration for a single sinner
 * Note: skillEA is NOT stored here - it's a sinner-level attribute managed separately
 */
export interface SinnerEquipment {
  identity: EquippedIdentity
  egos: EGOSlots
}

/**
 * Deployment configuration
 */
export interface DeploymentConfig {
  maxDeployed: number // configurable, default 7
}

/**
 * Complete deck state
 */
export interface DeckState {
  equipment: Record<string, SinnerEquipment> // keyed by sinner name (PascalCase)
  deploymentOrder: number[] // array of sinner indices in order (0-11)
  deploymentConfig: DeploymentConfig
}

/**
 * Affinity count for EA calculation
 */
export interface AffinityCount {
  affinity: Affinity
  generated: number // from identity skills
  consumed: number // from EGO costs
}

/**
 * Keyword count for EA calculation
 */
export interface KeywordCount {
  keyword: string
  count: number
}

/**
 * Entity mode for DeckBuilder toggle
 */
export type EntityMode = 'identity' | 'ego'

/**
 * Filter state for DeckBuilder
 * Lifted to parent for persistence across pane open/close
 */
export interface DeckFilterState {
  /** Entity mode: identity or ego selection */
  entityMode: EntityMode
  /** Selected sinner names for filtering */
  selectedSinners: Set<string>
  /** Selected skill keywords for filtering */
  selectedKeywords: Set<string>
  /** Selected skill attribute types (affinities) */
  selectedAttributes: Set<SkillAttributeType>
  /** Selected attack types */
  selectedAtkTypes: Set<AtkType>
  /** Selected defense types — applied in identity mode only */
  selectedDefTypes: Set<DefType>
  /** Selected identity ranks — applied in identity mode only */
  selectedRaritys: Set<number>
  /** Selected EGO types — applied in EGO mode only */
  selectedEgoTypes: Set<EGOType>
  /** Selected seasons */
  selectedSeasons: Set<Season>
  /** Selected unit keywords — applied in identity mode only */
  selectedUnitKeywords: Set<string>
  /** Selected additional (battle) keywords */
  selectedBattleKeywords: Set<string>
  /** Free-text search query */
  searchQuery: string
}
