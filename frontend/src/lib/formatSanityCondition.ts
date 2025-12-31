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

import type { SanityConditionI18n } from '@/schemas/SanityConditionSchemas'
import type { SanityConditionType } from '@/lib/constants'

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
  const args = numberMatches ? numberMatches.map(n => parseInt(n, 10)) : []

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
 * Strips size and color tags from text, returning plain text.
 * Handles: <size=95%>content</size>, <color=red>content</color>
 *
 * @param text - Text with potential size/color tags
 * @returns Plain text with tags stripped
 */
export function stripSanityTags(text: string): string {
  // Strip <size=...>content</size> tags
  let result = text.replace(/<size=[^>]*>([\s\S]*?)<\/size>/g, '$1')
  // Strip <color=...>content</color> tags
  result = result.replace(/<color=[^>]*>([\s\S]*?)<\/color>/g, '$1')
  return result
}

/**
 * Formats a sanity condition into a human-readable description.
 *
 * This is the main entry point for formatting sanity conditions.
 * Uses forgiving strategy: returns raw name if parsing/lookup fails.
 *
 * @param encodedName - Raw function name like "OnKillEnemyAsLevelRatioMultiply10"
 * @param i18n - Sanity condition i18n lookup object
 * @param type - Whether this is an increment ('inc') or decrement ('dec') condition
 * @returns Formatted description or raw name on failure
 *
 * @example
 * const i18n = {
 *   "OnKillEnemyAsLevelRatioMultiply": {
 *     "inc": "Increase by {0} after defeating...",
 *     "dec": "Decrease by {0} after defeating..."
 *   }
 * }
 * formatSanityCondition("OnKillEnemyAsLevelRatioMultiply10", i18n, "inc")
 * // => "Increase by 10 after defeating..."
 */
export function formatSanityCondition(
  encodedName: string,
  i18n: SanityConditionI18n,
  type: SanityConditionType
): string {
  // Parse the encoded name
  const { baseName, args } = parseSanityCondition(encodedName)

  // Look up the translation - use Object.hasOwn for runtime safety
  if (!Object.hasOwn(i18n, baseName)) {
    // Fallback: return raw name for debugging visibility
    console.warn(`[SanityCondition] Missing i18n for: ${baseName} (raw: ${encodedName})`)
    return encodedName
  }

  const template = i18n[baseName][type]

  // Substitute arguments and strip formatting tags
  const substituted = substituteArgs(template, args)
  return stripSanityTags(substituted)
}

/**
 * Formats multiple sanity conditions into an array of descriptions.
 *
 * @param encodedNames - Array of raw function names
 * @param i18n - Sanity condition i18n lookup object
 * @param type - Whether these are increment ('inc') or decrement ('dec') conditions
 * @returns Array of formatted descriptions
 */
export function formatSanityConditions(
  encodedNames: string[],
  i18n: SanityConditionI18n,
  type: SanityConditionType
): string[] {
  return encodedNames.map(name => formatSanityCondition(name, i18n, type))
}
