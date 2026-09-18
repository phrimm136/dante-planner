import { useSuspenseQuery } from '@tanstack/react-query'

import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { FontAdvanceTableSchema, type FontAdvanceTable } from './fontAdvances'

const TABLE_LANGUAGES = ['KR', 'EN', 'JP', 'CN'] as const

export type FontTableLanguage = (typeof TABLE_LANGUAGES)[number]

/** The table a language outside `supportedLngs` is measured against, as i18n falls back. */
const FALLBACK_TABLE_LANGUAGE: FontTableLanguage = 'EN'

const fontAdvanceQueryKeys = {
  face: (language: FontTableLanguage) => ['fontAdvances', language] as const,
}

export function fontTableLanguage(language: string): FontTableLanguage {
  return TABLE_LANGUAGES.find((candidate) => candidate === language) ?? FALLBACK_TABLE_LANGUAGE
}

function createFontAdvanceQueryOptions(language: FontTableLanguage) {
  return createStaticDataQueryOptions(
    fontAdvanceQueryKeys.face(language),
    () => import(`@static/data/fontAdvances/${language}.json`),
    FontAdvanceTableSchema,
    `fontAdvances ${language}`,
  )
}

export function useFontAdvances(language: string): FontAdvanceTable {
  const { data } = useSuspenseQuery(createFontAdvanceQueryOptions(fontTableLanguage(language)))
  return data
}
