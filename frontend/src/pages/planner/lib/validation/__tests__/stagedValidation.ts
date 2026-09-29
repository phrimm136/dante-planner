import type { EGOGiftSpec } from '@/pages/egoGift'
import type { MDCategory } from '@/shared/gameData'
import { validatePlannerForSync } from '../plannerValidation'
import type { PlannerIdRegistry } from '../plannerValidation'
import type { PlannerValidationError } from '../plannerValidationErrors'
import type { MDPlannerContent, SaveablePlanner } from '../../../types/PlannerTypes'

export type StagedResult = { isValid: boolean; errors: PlannerValidationError[] }

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
  const validated = validatePlannerForSync(planner, egoGiftSpec, egoGiftI18n, registry)
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
