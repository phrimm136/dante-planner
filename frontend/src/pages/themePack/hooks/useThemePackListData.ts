import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { ThemePackListSchema, ThemePackI18nSchema } from '../schemas/ThemePackSchemas'

// `data()` deviates from the standard list shape (`spec()`) — kept hand-rolled
export const themePackListQueryKeys = {
  all: () => ['themePack', 'list'] as const,
  data: () => ['themePack', 'list', 'data'] as const,
  i18n: (language: string) => ['themePack', 'list', 'i18n', language] as const,
}

function createThemePackListQueryOptions() {
  return createStaticDataQueryOptions(
    themePackListQueryKeys.data(),
    () => import('@static/data/themePackList.json'),
    ThemePackListSchema,
    'themePack list',
  )
}

function createThemePackI18nQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    themePackListQueryKeys.i18n(language),
    () => import(`@static/i18n/${language}/themePack.json`),
    ThemePackI18nSchema,
    'themePack i18n',
  )
}

/**
 * Hook that loads theme pack i18n only (language-dependent)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Use this for components that only need translated names.
 *
 * @returns Theme pack i18n map (ID -> {name, specialName?})
 */
export function useThemePackI18n() {
  const { i18n } = useTranslation()
  const { data: themePackI18n } = useSuspenseQuery(createThemePackI18nQueryOptions(i18n.language))
  return themePackI18n
}

/**
 * Hook that loads and validates theme pack list data
 * Suspends while loading - wrap in Suspense boundary
 *
 * @returns Validated theme pack spec map and i18n data
 */
export function useThemePackListData() {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createThemePackListQueryOptions())
  const { data: i18nData } = useSuspenseQuery(createThemePackI18nQueryOptions(i18n.language))

  return {
    spec,
    i18n: i18nData,
  }
}
