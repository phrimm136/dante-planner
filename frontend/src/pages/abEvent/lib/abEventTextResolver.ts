/**
 * abEventTextResolver.ts
 *
 * Effect text resolution for abnormality event detail page.
 * Resolves effect templates, conditions, and targets from _shared.json data.
 */

import type { AbEventShared } from '../schemas/AbEventSchemas'

/** Sin affinity colors from game ChoiceEventEffect color tags */
export const AFFINITY_COLORS: Record<string, string> = {
  CRIMSON: '#a0392b',
  SCARLET: '#bb521f',
  AMBER: '#e48801',
  SHAMROCK: '#61822b',
  AZURE: '#306471',
  INDIGO: '#185188',
  VIOLET: '#7d4e94',
}

/** Default cream text color used in game effect templates */
const CREAM = '#ebcaa2'

export interface CoinTossI18nContext {
  affinityNames: Record<string, string>
  unitKeywords: Record<string, string>
  sinnerNames: Record<string, string>
  identityNames: Record<string, string>
  successLabel: string
  failureLabel: string
}

/**
 * Resolve condition to self-contained color-tagged string.
 *
 * Game pattern for NotIncludeSkillAttribute_CRIMSON,VIOLET:
 *   <color=#a0392b>분노 속성 공격 스킬</color><color=#ebcaa2>을 보유하지 않은, </color>
 *   <color=#7d4e94>질투 속성 공격 스킬</color><color=#ebcaa2>을 보유하지 않은 </color>
 */
export function resolveCondition(condition: string, shared: AbEventShared): string {
  if (shared.keywords[condition]) return `<color=${CREAM}>${shared.keywords[condition]}</color>`

  const knownKeywords = ['IncludeSkillAttribute', 'NotIncludeSkillAttribute']
  for (const kw of knownKeywords) {
    if (condition.startsWith(`${kw}_`)) {
      const affinityPart = condition.slice(kw.length + 1)
      const kwTemplate = shared.keywords[kw]
      if (!kwTemplate) break

      const affinities = affinityPart.split(',')
      const phrases = affinities.map((aff) => {
        const sinName = shared.affinities?.[aff] ?? aff
        const sinColor = AFFINITY_COLORS[aff] ?? '#ccc'
        const resolved = kwTemplate.replace('[ATTRIBUTES]', sinName)

        for (const skillWord of ['스킬', 'スキル', 'Skill']) {
          const idx = resolved.indexOf(skillWord)
          if (idx !== -1) {
            const skillPart = resolved.slice(0, idx + skillWord.length)
            const restPart = resolved.slice(idx + skillWord.length)
            return `<color=${sinColor}>${skillPart}</color><color=${CREAM}>${restPart}</color>`
          }
        }
        return `<color=${sinColor}>${resolved}</color>`
      })

      return phrases.join(`<color=${CREAM}>, </color>`) + ' '
    }
  }

  return condition
}

/**
 * Resolve effect type to display text with template substitution.
 */
export function createEffectTextResolver(
  shared: AbEventShared,
  giftNames: Record<string, string>,
) {
  return function resolveEffectText(
    effectType: string,
    giftId?: number,
    amount?: number,
    target?: string,
    condition?: string,
    descId?: string,
  ): string | null {
    if (descId && shared.effects[descId]) {
      return shared.effects[descId]
    }

    let template = shared.effects[effectType]
    const numericSuffixes: number[] = []
    if (!template) {
      const parts = effectType.split('_')
      while (parts.length > 1 && /^\d+$/.test(parts[parts.length - 1])) {
        numericSuffixes.unshift(parseInt(parts.pop()!, 10))
      }
      if (numericSuffixes.length > 0) {
        template = shared.effects[parts.join('_')]
      }
    }
    if (!template) return null

    const extractedAmount = amount

    if (giftId && template.includes('{reward}')) {
      const giftName = giftNames[String(giftId)] ?? String(giftId)
      template = template.replace('{reward}', `"${giftName}"`)
    }

    if (template.includes('{hpAmount}') && numericSuffixes.length >= 2) {
      template = template.replace('{hpAmount}', String(numericSuffixes[0]))
      template = template.replace('{mpAmount}', String(numericSuffixes[1]))
    } else if (template.includes('{amount}')) {
      const amt = extractedAmount ?? numericSuffixes[0]
      if (amt !== undefined) {
        template = template.replace('{amount}', String(amt))
      }
    }

    if (template.includes('{conditions}') || template.includes('{targets}')) {
      let resolvedTarget = ''
      if (target) {
        const baseTarget = target.replace(/_\d+$/, '')
        resolvedTarget = shared.targets[baseTarget] ?? shared.sinnerNames?.[baseTarget] ?? target
      }

      if (condition) {
        const resolvedCond = resolveCondition(condition, shared)
        template = template.replace(
          new RegExp(`<color=${CREAM}>\\{conditions\\}\\{targets\\}</color>`),
          `${resolvedCond}<color=${CREAM}>${resolvedTarget}</color>`
        )
        template = template.replace('{conditions}', resolvedCond)
        template = template.replace('{targets}', `<color=${CREAM}>${resolvedTarget}</color>`)
      } else {
        template = template.replace('{conditions}', '')
        template = template.replace('{targets}', resolvedTarget)
      }
    }

    return template
  }
}

/**
 * Format adder info with i18n lookups.
 */
export function formatAdderInfo(
  adderInfo: { correctionCase: string; adder: number }[],
  unitKeywords: Record<string, string>,
  sinnerNames: Record<string, string>,
  identityNames?: Record<string, string>,
): string[] {
  return adderInfo.map((ai) => {
    const raw = ai.correctionCase
    let label = raw
    if (raw.startsWith('SpecificAssociation_')) {
      const keys = raw.replace('SpecificAssociation_', '').split(',')
      label = keys.map((k) => unitKeywords[k] ?? k.replace(/_/g, ' ')).join(' / ')
    } else if (raw.startsWith('SpecificKeyword_')) {
      const key = raw.replace('SpecificKeyword_', '')
      label = unitKeywords[key] ?? key.replace(/_/g, ' ')
    } else if (raw.startsWith('SpecificUnit_')) {
      const key = raw.replace('SpecificUnit_', '')
      label = sinnerNames[key] ?? key
    } else if (raw.startsWith('SpecificPersonality_')) {
      const ids = raw.replace('SpecificPersonality_', '').split(',')
      label = ids.map((id) => identityNames?.[id] ?? id).join(', ')
    }
    const sign = ai.adder > 0 ? '+' : ''
    return `${label} ${sign}${ai.adder}`
  })
}
