import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createEntityDetailQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { ThemePackDetailSchema, ThemePackI18nSchema } from '../schemas/ThemePackSchemas'

export const themePackDetailQueryKeys = {
  ...createEntityDetailQueryKeys('themePack'),
  // Deviates from the standard detail shape: all translations live in one
  // shared file, so detail reuses the list i18n cache entry
  i18n: (language: string) => ['themePack', 'list', 'i18n', language] as const,
}

function createThemePackDetailQueryOptions(id: string) {
  return createStaticDataQueryOptions(
    themePackDetailQueryKeys.detail(id),
    () => import(`@static/data/themePack/${id}.json`),
    ThemePackDetailSchema,
    `themePack / ${id}`,
  )
}

// Reuse the i18n query from list data (same file: themePack.json)
function createThemePackI18nQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    themePackDetailQueryKeys.i18n(language),
    () => import(`@static/i18n/${language}/themePack.json`),
    ThemePackI18nSchema,
    'themePack i18n',
  )
}

/**
 * Hook that loads theme pack detail spec data (individual file with egoGiftPool, eventPool, etc.)
 *
 * @param id - Theme pack ID
 * @returns Validated theme pack detail data
 */
export function useThemePackDetailSpec(id: string) {
  const { data: spec } = useSuspenseQuery(createThemePackDetailQueryOptions(id))
  return spec
}

/**
 * Hook that loads theme pack detail data (spec + i18n)
 * Suspends while loading - wrap in Suspense boundary
 *
 * @param id - Theme pack ID
 * @returns Validated theme pack detail spec and i18n
 */
export function useThemePackDetailData(id: string) {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createThemePackDetailQueryOptions(id))
  const { data: i18nData } = useSuspenseQuery(createThemePackI18nQueryOptions(i18n.language))

  return {
    spec,
    i18n: i18nData,
  }
}
