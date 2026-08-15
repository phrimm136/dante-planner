/**
 * abEventTextResolver.ts
 *
 * Effect text resolution for abnormality event detail page.
 * Resolves effect templates, conditions, and targets from _shared.json data.
 */

import { AFFINITY_COLORS } from '@/lib/constants'

import type { AbEventShared } from '../schemas/AbEventSchemas'

/** Default cream text color used in game effect templates */
const CREAM = '#ebcaa2'

/** Template shape where condition and target share one cream-colored span */
const CREAM_WRAPPED_PAIR = `<color=${CREAM}>{conditions}{targets}</color>`

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
export function createEffectTextResolver(shared: AbEventShared, giftNames: Record<string, string>) {
  return function resolveEffectText(
    effectType: string,
    giftId?: string,
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
      const giftName = giftNames[giftId] ?? giftId
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
        const creamTarget = `<color=${CREAM}>${resolvedTarget}</color>`
        if (template.includes(CREAM_WRAPPED_PAIR)) {
          template = template.replace(CREAM_WRAPPED_PAIR, `${resolvedCond}${creamTarget}`)
        } else {
          template = template.replace('{conditions}', resolvedCond)
          template = template.replace('{targets}', creamTarget)
        }
      } else {
        template = template.replace('{conditions}', '')
        template = template.replace('{targets}', resolvedTarget)
      }
    }

    return template
  }
}

interface AdderNameContext {
  unitKeywords: Record<string, string>
  sinnerNames: Record<string, string>
  identityNames?: Record<string, string>
}

const ADDER_RESOLVERS: Array<[string, (rest: string, ctx: AdderNameContext) => string]> = [
  [
    'SpecificAssociation_',
    (rest, ctx) =>
      rest
        .split(',')
        .map((key) => ctx.unitKeywords[key] ?? key.replace(/_/g, ' '))
        .join(' / '),
  ],
  ['SpecificKeyword_', (rest, ctx) => ctx.unitKeywords[rest] ?? rest.replace(/_/g, ' ')],
  ['SpecificUnit_', (rest, ctx) => ctx.sinnerNames[rest] ?? rest],
  [
    'SpecificPersonality_',
    (rest, ctx) =>
      rest
        .split(',')
        .map((id) => ctx.identityNames?.[id] ?? id)
        .join(', '),
  ],
]

/**
 * Format adder info with i18n lookups.
 */
export function formatAdderInfo(
  adderInfo: { correctionCase: string; adder: number }[],
  unitKeywords: Record<string, string>,
  sinnerNames: Record<string, string>,
  identityNames?: Record<string, string>,
): string[] {
  const ctx: AdderNameContext = { unitKeywords, sinnerNames, identityNames }
  return adderInfo.map((ai) => {
    const raw = ai.correctionCase
    const match = ADDER_RESOLVERS.find(([prefix]) => raw.startsWith(prefix))
    const label = match ? match[1](raw.slice(match[0].length), ctx) : raw
    const sign = ai.adder > 0 ? '+' : ''
    return `${label} ${sign}${ai.adder}`
  })
}
