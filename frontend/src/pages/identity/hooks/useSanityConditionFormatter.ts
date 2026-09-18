import { useSanityConditionI18n } from '@/shared/gameText'
import { formatSanityCondition } from '../lib/formatSanityCondition'
import type { SanityConditionResult } from '../lib/formatSanityCondition'
import type { SanityConditionType } from '@/shared/gameData'

function resolve(result: SanityConditionResult, encodedName: string): string {
  if (result.ok) return result.value

  console.warn(`[SanityCondition] Missing i18n for: ${result.error.baseName} (raw: ${encodedName})`)
  return encodedName
}

export function useSanityConditionFormatter() {
  const i18n = useSanityConditionI18n()

  return {
    format: (encodedName: string, type: SanityConditionType): string => {
      return resolve(formatSanityCondition(encodedName, i18n, type), encodedName)
    },

    formatAll: (encodedNames: string[], type: SanityConditionType): string[] => {
      return encodedNames.map((encodedName) =>
        resolve(formatSanityCondition(encodedName, i18n, type), encodedName),
      )
    },

    i18n,
  }
}
