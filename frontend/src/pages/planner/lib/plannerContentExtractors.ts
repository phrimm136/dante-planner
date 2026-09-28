import { decodeGiftSelection } from '@/pages/egoGift'
import { EncodedGiftIdSchema, floorCount } from '@/shared/gameData'
import type { MDCategory } from '@/shared/gameData'
import { isMDPlanner } from '../types/PlannerTypes'
import type { MDPlannerContent, SaveablePlanner } from '../types/PlannerTypes'
import type { PlannerSearchFilters } from '../types/PlannerSearchTypes'

export function extractIdentityIds(content: MDPlannerContent): Set<string> {
  const ids = new Set<string>()
  const equipment = content.equipment
  if (!equipment) return ids

  for (const sinnerId of Object.keys(equipment)) {
    const sinnerEquip = equipment[sinnerId]
    if (sinnerEquip?.identity?.id) {
      ids.add(String(sinnerEquip.identity.id))
    }
  }

  return ids
}

export function extractEgoIds(content: MDPlannerContent): Set<string> {
  const ids = new Set<string>()
  const equipment = content.equipment
  if (!equipment) return ids

  for (const sinnerId of Object.keys(equipment)) {
    const egos = equipment[sinnerId]?.egos
    if (!egos) continue

    for (const egoType of Object.keys(egos)) {
      const ego = egos[egoType as keyof typeof egos]
      if (ego?.id) {
        ids.add(String(ego.id))
      }
    }
  }

  return ids
}

export function extractGiftIds(content: MDPlannerContent, category: MDCategory): Set<string> {
  const ids = new Set<string>()

  // Content stores an enhanced gift as its level prefixed onto the four-digit base (19154 and
  // 29154 both mean 9154); the filter index and the filter chips both address the base.
  const addIds = (source: Iterable<string> | undefined | null) => {
    if (!source) return
    for (const id of source) {
      const parsed = EncodedGiftIdSchema.safeParse(id)
      if (parsed.success) ids.add(decodeGiftSelection(parsed.data).giftId)
    }
  }

  addIds(content.selectedGiftIds)
  addIds(content.observationGiftIds)
  addIds(content.comprehensiveGiftIds)

  if (content.floorSelections) {
    for (const floor of content.floorSelections.slice(0, floorCount(category))) {
      addIds(floor?.giftIds)
    }
  }

  return ids
}

export function extractThemePackIds(content: MDPlannerContent, category: MDCategory): Set<string> {
  const ids = new Set<string>()

  if (!content.floorSelections) return ids

  for (const floor of content.floorSelections.slice(0, floorCount(category))) {
    if (floor?.themePackId != null) {
      ids.add(String(floor.themePackId))
    }
  }

  return ids
}

function containsAll(extracted: Set<string>, requiredIds: string[]): boolean {
  if (requiredIds.length === 0) return true
  return requiredIds.every((id) => extracted.has(String(id)))
}

export function matchesPlannerFilters(
  plan: SaveablePlanner,
  filters: PlannerSearchFilters,
): boolean {
  if (filters.title) {
    const titleLower = plan.metadata.title.toLowerCase()
    if (!titleLower.includes(filters.title.toLowerCase())) {
      return false
    }
  }

  if (!isMDPlanner(plan)) {
    const hasContentFilters =
      filters.keywords.length > 0 ||
      filters.identityIds.length > 0 ||
      filters.egoIds.length > 0 ||
      filters.giftIds.length > 0 ||
      filters.themePackIds.length > 0
    return !hasContentFilters
  }

  const { content } = plan
  const { category } = plan.config

  if (filters.keywords.length > 0) {
    const planKeywords = content.selectedKeywords
    if (!planKeywords) return false

    const keywordSet =
      planKeywords instanceof Set ? (planKeywords as Set<string>) : new Set(planKeywords)

    if (!filters.keywords.every((kw) => keywordSet.has(kw))) {
      return false
    }
  }

  if (filters.identityIds.length > 0) {
    if (!containsAll(extractIdentityIds(content), filters.identityIds)) {
      return false
    }
  }

  if (filters.egoIds.length > 0) {
    if (!containsAll(extractEgoIds(content), filters.egoIds)) {
      return false
    }
  }

  if (filters.giftIds.length > 0) {
    if (!containsAll(extractGiftIds(content, category), filters.giftIds)) {
      return false
    }
  }

  if (filters.themePackIds.length > 0) {
    if (!containsAll(extractThemePackIds(content, category), filters.themePackIds)) {
      return false
    }
  }

  return true
}
