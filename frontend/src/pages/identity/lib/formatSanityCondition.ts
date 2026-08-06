/**
 * Sanity Condition Formatter
 *
 * Parses encoded sanity condition function names and formats them
 * into human-readable i18n descriptions.
 *
 * Encoding pattern:
 * - Raw: "OnKillEnemyAsLevelRatioMultiply10"
 * - Parsed: { baseName: "OnKillEnemyAsLevelRatioMultiply", args: [10] }
 * - Output: "Increase by 10 after this unit defeats an enemy..."
 *
 * Multiple args example:
 * - Raw: "OnWinDuelAsParryingCountMultiply10AndPlus20Percent"
 * - Parsed: { baseName: "OnWinDuelAsParryingCountMultiplyAndPlusPercent", args: [10, 20] }
 */

import { err, ok } from '@/lib/result'

import type { Result } from '@/lib/result'
import type { SanityConditionI18n } from '@/shared/gameText'
import type { SanityConditionType } from '@/shared/gameData'

/** Base name a condition parsed to, for which the i18n table holds no entry. */
export interface MissingSanityConditionI18n {
  baseName: string
}

/** A formatted description, or the base name whose translation is missing. */
export type SanityConditionResult = Result<string, MissingSanityConditionI18n>

/**
 * Result of parsing a sanity condition function name
 */
export interface ParsedSanityCondition {
  /** Base function name without numeric arguments */
  baseName: string
  /** Extracted numeric arguments in order */
  args: number[]
}

/**
 * Parses an encoded sanity condition function name into base name and arguments.
 *
 * @param encodedName - Raw function name like "OnKillEnemyAsLevelRatioMultiply10"
 * @returns Parsed result with baseName and args array
 *
 * @example
 * parseSanityCondition("OnKillEnemyAsLevelRatioMultiply10")
 * // => { baseName: "OnKillEnemyAsLevelRatioMultiply", args: [10] }
 *
 * @example
 * parseSanityCondition("OnWinDuelAsParryingCountMultiply10AndPlus20Percent")
 * // => { baseName: "OnWinDuelAsParryingCountMultiplyAndPlusPercent", args: [10, 20] }
 */
export function parseSanityCondition(encodedName: string): ParsedSanityCondition {
  // Extract all number sequences from the string
  const numberMatches = encodedName.match(/\d+/g)
  const args = numberMatches ? numberMatches.map((n) => parseInt(n, 10)) : []

  // Remove all digits to get the base function name
  const baseName = encodedName.replace(/\d+/g, '')

  return { baseName, args }
}

/**
 * Substitutes placeholder arguments {0}, {1}, {2} in a template string.
 *
 * @param template - Template string with {0}, {1}, etc. placeholders
 * @param args - Array of values to substitute
 * @returns String with placeholders replaced by values
 *
 * @example
 * substituteArgs("Increase by {0} after defeating enemy", [10])
 * // => "Increase by 10 after defeating enemy"
 */
export function substituteArgs(template: string, args: number[]): string {
  let result = template
  for (let i = 0; i < args.length; i++) {
    // Use replaceAll for better performance (no regex compilation)
    result = result.replaceAll(`{${String(i)}}`, String(args[i]))
  }
  return result
}

/**
 * Formats a sanity condition into a human-readable description.
 *
 * This is the main entry point for formatting sanity conditions.
 *
 * @param encodedName - Raw function name like "OnKillEnemyAsLevelRatioMultiply10"
 * @param i18n - Sanity condition i18n lookup object
 * @param type - Whether this is an increment ('inc') or decrement ('dec') condition
 * @returns The formatted description, or the unresolved base name
 *
 * @example
 * const i18n = {
 *   "OnKillEnemyAsLevelRatioMultiply": {
 *     "inc": "Increase by {0} after defeating...",
 *     "dec": "Decrease by {0} after defeating..."
 *   }
 * }
 * formatSanityCondition("OnKillEnemyAsLevelRatioMultiply10", i18n, "inc")
 * // => { ok: true, value: "Increase by 10 after defeating..." }
 */
export function formatSanityCondition(
  encodedName: string,
  i18n: SanityConditionI18n,
  type: SanityConditionType,
): SanityConditionResult {
  const { baseName, args } = parseSanityCondition(encodedName)

  // Object.hasOwn for runtime safety against prototype keys
  if (!Object.hasOwn(i18n, baseName)) {
    return err({ baseName })
  }

  // Substitute arguments - keep tags for FormattedSanityText to parse
  return ok(substituteArgs(i18n[baseName][type], args))
}

/**
 * Formats multiple sanity conditions.
 *
 * @param encodedNames - Array of raw function names
 * @param i18n - Sanity condition i18n lookup object
 * @param type - Whether these are increment ('inc') or decrement ('dec') conditions
 * @returns One result per input, in order
 */
export function formatSanityConditions(
  encodedNames: string[],
  i18n: SanityConditionI18n,
  type: SanityConditionType,
): SanityConditionResult[] {
  return encodedNames.map((name) => formatSanityCondition(name, i18n, type))
}
