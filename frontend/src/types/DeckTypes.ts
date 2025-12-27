import type { EgoType } from './EGOTypes'
import type { Affinity } from '@/lib/constants'

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
export type EGOSlots = {
  [K in EgoType]?: EquippedEGO
}

/**
 * Complete equipment configuration for a single sinner
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
