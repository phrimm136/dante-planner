import { useSuspenseQuery } from '@tanstack/react-query'

import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { FontAdvanceTableSchema, type FontAdvanceTable } from './fontAdvances'

const TABLE_LANGUAGES = ['KR', 'EN', 'JP', 'CN'] as const

export type FontTableLanguage = (typeof TABLE_LANGUAGES)[number]

export type FontTableRole = 'name' | 'description' | 'cost'

/** The table a language outside `supportedLngs` is measured against, as i18n falls back. */
const FALLBACK_TABLE_LANGUAGE: FontTableLanguage = 'EN'

const fontAdvanceQueryKeys = {
  face: (role: FontTableRole, language: FontTableLanguage) =>
    ['fontAdvances', role, language] as const,
}

const TABLE_IMPORTERS: Record<
  FontTableRole,
  (language: FontTableLanguage) => Promise<{ default: unknown }>
> = {
  name: (language) => import(`@static/data/fontAdvances/${language}.json`),
  description: (language) => import(`@static/data/fontAdvances/description/${language}.json`),
  cost: (language) => import(`@static/data/fontAdvances/cost/${language}.json`),
}

export function fontTableLanguage(language: string): FontTableLanguage {
  return TABLE_LANGUAGES.find((candidate) => candidate === language) ?? FALLBACK_TABLE_LANGUAGE
}

export function createFontAdvanceQueryOptions(role: FontTableRole, language: FontTableLanguage) {
  return createStaticDataQueryOptions(
    fontAdvanceQueryKeys.face(role, language),
    () => TABLE_IMPORTERS[role](language),
    FontAdvanceTableSchema,
    `fontAdvances ${role} ${language}`,
  )
}

export function useFontAdvances(language: string, role: FontTableRole = 'name'): FontAdvanceTable {
  const { data } = useSuspenseQuery(
    createFontAdvanceQueryOptions(role, fontTableLanguage(language)),
  )
  return data
}
