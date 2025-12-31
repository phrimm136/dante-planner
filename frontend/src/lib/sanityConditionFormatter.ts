/**
 * Sanity Condition Formatter Hook
 *
 * Provides formatting functions for sanity conditions with i18n support.
 * Separated from useSanityConditionData.ts following Single Responsibility Principle.
 */

import { useSanityConditionI18n } from '@/hooks/useSanityConditionData'
import { formatSanityCondition, formatSanityConditions } from '@/lib/formatSanityCondition'
import type { SanityConditionType } from '@/lib/constants'

/**
 * Hook that provides a formatter function for sanity conditions.
 * Suspends while loading i18n data - wrap in Suspense boundary.
 *
 * Returns functions to format single or multiple conditions with
 * automatic language reactivity.
 *
 * @returns Formatter functions for sanity conditions
 *
 * @example
 * ```tsx
 * function SanityDisplay() {
 *   const { format, formatAll } = useSanityConditionFormatter();
 *
 *   // Format single condition
 *   const desc = format("OnKillEnemyAsLevelRatioMultiply10", "inc");
 *   // => "Increase by 10 after this unit defeats an enemy..."
 *
 *   // Format array of conditions
 *   const descs = formatAll(["OnKillEnemy10", "OnDieAlly5"], "dec");
 * }
 * ```
 */
export function useSanityConditionFormatter() {
  const i18n = useSanityConditionI18n()

  return {
    /**
     * Format a single sanity condition
     * @param encodedName - Raw function name like "OnKillEnemyAsLevelRatioMultiply10"
     * @param type - Whether this is increment ('inc') or decrement ('dec')
     * @returns Formatted description or raw name on failure
     */
    format: (encodedName: string, type: SanityConditionType): string => {
      return formatSanityCondition(encodedName, i18n, type)
    },

    /**
     * Format multiple sanity conditions
     * @param encodedNames - Array of raw function names
     * @param type - Whether these are increment ('inc') or decrement ('dec')
     * @returns Array of formatted descriptions
     */
    formatAll: (encodedNames: string[], type: SanityConditionType): string[] => {
      return formatSanityConditions(encodedNames, i18n, type)
    },

    /** Raw i18n data for advanced usage */
    i18n,
  }
}
