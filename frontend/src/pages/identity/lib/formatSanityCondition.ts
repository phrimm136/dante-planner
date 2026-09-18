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

export interface MissingSanityConditionI18n {
  baseName: string
}

export type SanityConditionResult = Result<string, MissingSanityConditionI18n>

export interface ParsedSanityCondition {
  baseName: string
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
  const numberMatches = encodedName.match(/\d+/g)
  const args = numberMatches ? numberMatches.map((n) => parseInt(n, 10)) : []

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
    result = result.replaceAll(`{${String(i)}}`, String(args[i]))
  }
  return result
}

export function formatSanityCondition(
  encodedName: string,
  i18n: SanityConditionI18n,
  type: SanityConditionType,
): SanityConditionResult {
  const { baseName, args } = parseSanityCondition(encodedName)

  // Object.hasOwn for runtime safety against prototype keys
  const entry = Object.hasOwn(i18n, baseName) ? i18n[baseName] : undefined
  if (entry === undefined) {
    return err({ baseName })
  }

  return ok(substituteArgs(entry[type], args))
}

export function formatSanityConditions(
  encodedNames: string[],
  i18n: SanityConditionI18n,
  type: SanityConditionType,
): SanityConditionResult[] {
  return encodedNames.map((name) => formatSanityCondition(name, i18n, type))
}
