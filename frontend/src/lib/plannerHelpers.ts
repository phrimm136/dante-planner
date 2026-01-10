/**
 * Planner Utilities
 *
 * Pure functions for planner-related validation and checks.
 */

import type { FloorThemeSelection } from '@/types/PlannerTypes'

/**
 * Validation error type for floor theme pack validation
 */
export interface FloorValidationError {
  /** 0-indexed floor that failed validation */
  floorIndex: number
  /** 1-indexed floor number for display */
  floorNumber: number
  /** Error message describing the validation failure */
  message: string
}

/**
 * Checks if a floor's theme pack selector should be enabled based on previous floor state
 *
 * Rules:
 * - Floor 1 (index 0): Always enabled (no prerequisites)
 * - Floors 2-15: Enabled only if the previous floor has a theme pack selected
 *
 * @param floorIndex - 0-indexed floor position (0 for floor 1, 1 for floor 2, etc.)
 * @param floorSelections - Array of all floor selections
 * @returns true if theme pack selector should be enabled, false if disabled
 *
 * @example
 * // Floor 1 is always enabled
 * canSelectFloorThemePack(0, floors) // Returns true
 *
 * @example
 * // Floor 2 enabled only if floor 1 has theme pack
 * const floors = [{ themePackId: '123', ... }, { themePackId: null, ... }]
 * canSelectFloorThemePack(1, floors) // Returns true (floor 1 has pack)
 *
 * @example
 * // Floor 3 disabled if floor 2 has no theme pack
 * const floors = [{ ... }, { themePackId: null, ... }, { ... }]
 * canSelectFloorThemePack(2, floors) // Returns false (floor 2 has no pack)
 */
export function canSelectFloorThemePack(
  floorIndex: number,
  floorSelections: FloorThemeSelection[]
): boolean {
  // First floor always enabled
  if (floorIndex === 0) return true

  // Other floors require previous floor to have a theme pack
  return floorSelections[floorIndex - 1].themePackId !== null
}

/**
 * Validates that all floor theme pack selections meet save requirements
 *
 * Rules enforced:
 * 1. Each floor must have a theme pack selected (no null values)
 * 2. Progressive prerequisite: Floor N can only have a theme pack if floor N-1 has one
 *
 * @param floorSelections - Array of floor selections to validate
 * @param floorCount - Number of active floors (5, 10, or 15)
 * @returns Array of validation errors (empty if valid)
 *
 * @example
 * // Valid: All floors have theme packs
 * const floors = [
 *   { themePackId: '1001', difficulty: 0, giftIds: new Set() },
 *   { themePackId: '1002', difficulty: 0, giftIds: new Set() },
 * ]
 * validateFloorThemePacksForSave(floors, 2) // Returns []
 *
 * @example
 * // Invalid: Floor 2 missing theme pack
 * const floors = [
 *   { themePackId: '1001', difficulty: 0, giftIds: new Set() },
 *   { themePackId: null, difficulty: 0, giftIds: new Set() },
 * ]
 * validateFloorThemePacksForSave(floors, 2)
 * // Returns [{ floorIndex: 1, floorNumber: 2, message: "Floor 2 must have a theme pack selected" }]
 *
 * @example
 * // Invalid: Floor 3 has theme pack but floor 2 doesn't (prerequisite violation)
 * const floors = [
 *   { themePackId: '1001', difficulty: 0, giftIds: new Set() },
 *   { themePackId: null, difficulty: 0, giftIds: new Set() },
 *   { themePackId: '1003', difficulty: 0, giftIds: new Set() },
 * ]
 * validateFloorThemePacksForSave(floors, 3)
 * // Returns errors for both floor 2 (missing) and floor 3 (prerequisite violated)
 */
export function validateFloorThemePacksForSave(
  floorSelections: FloorThemeSelection[],
  floorCount: number
): FloorValidationError[] {
  const errors: FloorValidationError[] = []

  // Check only the active floors based on category (5F, 10F, 15F)
  for (let i = 0; i < floorCount; i++) {
    const floor = floorSelections[i]
    const floorNumber = i + 1

    // Rule 1: Each floor must have a theme pack
    if (!floor.themePackId) {
      errors.push({
        floorIndex: i,
        floorNumber,
        message: `Floor ${floorNumber} must have a theme pack selected`,
      })
      continue // Skip prerequisite check if floor is missing theme pack
    }

    // Rule 2: Progressive prerequisite (skip for floor 1)
    if (i > 0) {
      const previousFloor = floorSelections[i - 1]
      if (!previousFloor.themePackId) {
        errors.push({
          floorIndex: i,
          floorNumber,
          message: `Floor ${floorNumber} cannot have a theme pack because Floor ${i} is missing one`,
        })
      }
    }
  }

  return errors
}
