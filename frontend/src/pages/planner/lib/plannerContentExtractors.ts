/**
 * Planner Content Extractors
 *
 * Utilities for extracting entity IDs from MDPlannerContent and matching
 * plans against search filters. Used for local filtering of personal plans
 * stored in IndexedDB.
 *
 * Entity extraction traversal:
 * - IDENTITY: equipment[sinnerId].identity.id
 * - EGO: equipment[sinnerId].egos[egoType].id
 * - EGO_GIFT: selectedGiftIds + observationGiftIds + comprehensiveGiftIds + floorSelections[*].giftIds
 * - THEME_PACK: floorSelections[*].themePackId (non-null only)
 */

import type { MDPlannerContent, SaveablePlanner } from '../types/PlannerTypes'
import type { PlannerSearchFilters } from '../types/PlannerSearchTypes'

// ============================================================================
// Entity ID Extraction
// ============================================================================

/**
 * Extract all identity IDs from planner content.
 * Traverses equipment[sinnerId].identity.id for all sinners.
 */
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

/**
 * Extract all EGO IDs from planner content.
 * Traverses equipment[sinnerId].egos[egoType].id for all sinners and ego types.
 */
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

/**
 * Extract all gift IDs from planner content.
 * Union of: selectedGiftIds + observationGiftIds + comprehensiveGiftIds + floorSelections[*].giftIds
 *
 * Handles both string[] (serialized) and Set<string> (in-editor state) for each source.
 */
export function extractGiftIds(content: MDPlannerContent): Set<string> {
  const ids = new Set<string>()

  // Helper to add IDs from either array or Set
  const addIds = (source: Iterable<string> | undefined | null) => {
    if (!source) return
    for (const id of source) {
      ids.add(String(id))
    }
  }

  addIds(content.selectedGiftIds)
  addIds(content.observationGiftIds)
  addIds(content.comprehensiveGiftIds)

  if (content.floorSelections) {
    for (const floor of content.floorSelections) {
      addIds(floor?.giftIds)
    }
  }

  return ids
}

/**
 * Extract all theme pack IDs from planner content.
 * Traverses floorSelections[*].themePackId, collecting non-null values.
 */
export function extractThemePackIds(content: MDPlannerContent): Set<string> {
  const ids = new Set<string>()

  if (!content.floorSelections) return ids

  for (const floor of content.floorSelections) {
    if (floor?.themePackId != null) {
      ids.add(String(floor.themePackId))
    }
  }

  return ids
}

// ============================================================================
// Filter Matching
// ============================================================================

/**
 * Check if ALL required IDs exist in the extracted set.
 * Returns true if requiredIds is empty (no filter active).
 */
function containsAll(extracted: Set<string>, requiredIds: string[]): boolean {
  if (requiredIds.length === 0) return true
  return requiredIds.every((id) => extracted.has(String(id)))
}

/**
 * Check if a plan's content matches all active filters (AND semantics).
 *
 * Filter logic:
 * - title: case-insensitive substring match on metadata.title
 * - keywords: ALL selected keywords present in content.selectedKeywords
 * - identityIds: ALL selected IDs found across equipment entries
 * - egoIds: ALL selected IDs found across equipment entries
 * - giftIds: ALL selected IDs found in the union of all gift sources
 * - themePackIds: ALL selected IDs found across floor selections
 *
 * Returns true if plan passes all active filters.
 * Returns true if no filters are active.
 */
export function matchesPlannerFilters(
  plan: SaveablePlanner,
  filters: PlannerSearchFilters,
): boolean {
  // Title filter: case-insensitive substring match
  if (filters.title) {
    const titleLower = plan.metadata.title.toLowerCase()
    if (!titleLower.includes(filters.title.toLowerCase())) {
      return false
    }
  }

  // Content filters only apply to MD planners
  if (plan.config.type !== 'MIRROR_DUNGEON') {
    // For non-MD planners, content filters automatically fail if any are active
    const hasContentFilters =
      filters.keywords.length > 0 ||
      filters.identityIds.length > 0 ||
      filters.egoIds.length > 0 ||
      filters.giftIds.length > 0 ||
      filters.themePackIds.length > 0
    return !hasContentFilters
  }

  const content = plan.content as MDPlannerContent

  // Keyword filter: ALL selected keywords must be present
  if (filters.keywords.length > 0) {
    // Handle both Set<string> (editor state) and string[] (serialized)
    const planKeywords = content.selectedKeywords
    if (!planKeywords) return false

    const keywordSet = planKeywords instanceof Set
      ? planKeywords as Set<string>
      : new Set(planKeywords)

    if (!filters.keywords.every((kw) => keywordSet.has(kw))) {
      return false
    }
  }

  // Identity filter
  if (filters.identityIds.length > 0) {
    if (!containsAll(extractIdentityIds(content), filters.identityIds)) {
      return false
    }
  }

  // EGO filter
  if (filters.egoIds.length > 0) {
    if (!containsAll(extractEgoIds(content), filters.egoIds)) {
      return false
    }
  }

  // Gift filter
  if (filters.giftIds.length > 0) {
    if (!containsAll(extractGiftIds(content), filters.giftIds)) {
      return false
    }
  }

  // Theme pack filter
  if (filters.themePackIds.length > 0) {
    if (!containsAll(extractThemePackIds(content), filters.themePackIds)) {
      return false
    }
  }

  return true
}
