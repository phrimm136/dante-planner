import type { BuffEffect, StartBuffI18n, BattleKeywords } from '@/shared/gameText'
import { getKeywordName } from '@/shared/gameText'

/**
 * Replaces placeholders in text with effect values
 * Placeholders: {0} = value, {1} = value2
 * For referenceData: {0} = activeRound, {1} = buffKeyword (translated), {2} = stack, {3} = turn, {4} = limit
 */
function replacePlaceholders(
  text: string,
  effect: BuffEffect,
  battleKeywords?: BattleKeywords,
): string {
  let result = text

  if (effect.referenceData) {
    const { activeRound, buffKeyword, stack, turn, limit } = effect.referenceData
    if (activeRound !== undefined) result = result.replace('{0}', String(activeRound))
    if (buffKeyword !== undefined) {
      const translatedKeyword = battleKeywords
        ? getKeywordName(battleKeywords, buffKeyword)
        : buffKeyword
      result = result.replace('{1}', translatedKeyword)
    }
    if (stack !== undefined) result = result.replace('{2}', String(stack))
    if (turn !== undefined) result = result.replace('{3}', String(turn))
    if (limit !== undefined) result = result.replace('{4}', String(limit))
  } else {
    if (effect.value !== undefined) result = result.replace('{0}', String(effect.value))
    if (effect.value2 !== undefined) result = result.replace('{1}', String(effect.value2))
  }

  return result
}

function effectText(
  effect: BuffEffect,
  i18n: StartBuffI18n,
  battleKeywords?: BattleKeywords,
): string {
  const translationKey = effect.customLocalizeTextId || effect.type
  const template = i18n[translationKey] || translationKey
  return replacePlaceholders(template, effect, battleKeywords)
}

const EFFECT_SEPARATOR = '\n'

export function buffDescriptionText(
  effects: BuffEffect[],
  i18n: StartBuffI18n,
  bulletPrefix: string,
  battleKeywords?: BattleKeywords,
): string {
  return effects
    .map((effect) => `${bulletPrefix}${effectText(effect, i18n, battleKeywords)}`)
    .join(EFFECT_SEPARATOR)
}
