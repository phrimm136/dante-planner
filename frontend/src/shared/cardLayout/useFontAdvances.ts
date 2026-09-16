import { useSuspenseQuery } from '@tanstack/react-query'

import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { FontAdvanceTableSchema, type FontAdvanceTable } from './fontAdvances'

/** The languages the site ships a display-face advance table for. */
const TABLE_LANGUAGES = ['KR', 'EN', 'JP', 'CN'] as const

export type FontTableLanguage = (typeof TABLE_LANGUAGES)[number]

/** The table a language outside `supportedLngs` is measured against, as i18n falls back. */
const FALLBACK_TABLE_LANGUAGE: FontTableLanguage = 'EN'

// Hand-rolled: single-key namespace, one entry per display face.
const fontAdvanceQueryKeys = {
  face: (language: FontTableLanguage) => ['fontAdvances', language] as const,
}

/** The display face a language's card names are drawn in. */
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

/**
 * The advance table of a language's display face. Suspends while it loads — read it
 * inside a boundary shaped like the name it stands in for.
 */
export function useFontAdvances(language: string): FontAdvanceTable {
  const { data } = useSuspenseQuery(createFontAdvanceQueryOptions(fontTableLanguage(language)))
  return data
}
