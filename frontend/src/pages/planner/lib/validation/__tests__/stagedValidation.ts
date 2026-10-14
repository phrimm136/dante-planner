import type { EGOGiftSpec } from '@/pages/egoGift'
import { MAX_ENTITY_TIER } from '@/shared/gameData'
import type { MDCategory } from '@/shared/gameData'
import { validatePlannerForSync } from '../plannerValidation'
import type { PlannerIdRegistry } from '../plannerValidation'
import type { PlannerValidationError } from '../plannerValidationErrors'
import type { MDPlannerContent, SaveablePlanner } from '../../../types/PlannerTypes'

export type StagedResult = { isValid: boolean; errors: PlannerValidationError[] }

class EveryId extends Set<string> {
  override has(): boolean {
    return true
  }
}

class EveryEgoCeiling extends Map<string, number> {
  override get(): number {
    return MAX_ENTITY_TIER.ego
  }
}

class EveryStartGiftPool extends Map<string, ReadonlySet<string>> {
  override has(): boolean {
    return true
  }

  override get(): ReadonlySet<string> {
    return new EveryId()
  }
}

export const EVERY_ID_REGISTRY: PlannerIdRegistry = {
  identityIds: new EveryId(),
  egoIds: new EveryId(),
  egoMaxThreadspin: new EveryEgoCeiling(),
  themePackIds: new EveryId(),
  season: {
    startBuffIds: new EveryId([
      '100',
      '101',
      '102',
      '103',
      '104',
      '105',
      '106',
      '107',
      '108',
      '109',
    ]),
    startGiftPools: new EveryStartGiftPool(),
    identityMaxLevel: 60,
  },
}

export const EVERY_GIFT_SPEC: Record<string, EGOGiftSpec> = new Proxy({}, { has: () => true })

export const plannerOf = (
  published: boolean,
  title: string | undefined,
  content: MDPlannerContent,
  category: MDCategory,
): SaveablePlanner =>
  ({
    metadata: { published, title },
    config: { type: 'MIRROR_DUNGEON', category },
    content,
  }) as unknown as SaveablePlanner

function staged(
  planner: SaveablePlanner,
  egoGiftSpec: Record<string, EGOGiftSpec> | undefined,
  egoGiftI18n: Record<string, string> | undefined,
  registry: PlannerIdRegistry | undefined,
): StagedResult {
  const validated = validatePlannerForSync(
    planner,
    egoGiftSpec ?? EVERY_GIFT_SPEC,
    egoGiftI18n,
    registry ?? EVERY_ID_REGISTRY,
  )
  return validated.ok ? { isValid: true, errors: [] } : { isValid: false, errors: validated.error }
}

export const draftValidation = (
  content: MDPlannerContent,
  category: MDCategory,
  egoGiftSpec?: Record<string, EGOGiftSpec>,
  egoGiftI18n?: Record<string, string>,
  registry?: PlannerIdRegistry,
): StagedResult =>
  staged(plannerOf(false, undefined, content, category), egoGiftSpec, egoGiftI18n, registry)

export const publishValidation = (
  title: string | undefined,
  content: MDPlannerContent,
  category: MDCategory,
  egoGiftSpec?: Record<string, EGOGiftSpec>,
  egoGiftI18n?: Record<string, string>,
  registry?: PlannerIdRegistry,
): StagedResult =>
  staged(plannerOf(true, title, content, category), egoGiftSpec, egoGiftI18n, registry)
